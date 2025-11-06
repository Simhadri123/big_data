import java.io.IOException;
import java.util.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

/**
 * Partition
 * GraphPartition style motif counter with breakdown.
 */
public class Partition {

    private static List<int[]> combosContaining(int rho, int hu, int hv) {
        List<int[]> out = new ArrayList<>();
        for (int a = 0; a < rho; a++) {
            for (int b = a + 1; b < rho; b++) {
                for (int c = b + 1; c < rho; c++) {
                    for (int d = c + 1; d < rho; d++) {
                        boolean hasHu = (a == hu) || (b == hu) || (c == hu) || (d == hu);
                        boolean hasHv = (a == hv) || (b == hv) || (c == hv) || (d == hv);
                        if (hasHu && hasHv) out.add(new int[]{a, b, c, d});
                    }
                }
            }
        }
        return out;
    }

    public static class PartMapper extends Mapper<LongWritable, Text, Text, Text> {
        private int rho;
        @Override
        protected void setup(Context ctx) {
            rho = ctx.getConfiguration().getInt("motif.rho", 8);
        }
        private int h(String v) { return Math.abs(v.hashCode()) % rho; }
        private final Text outKey = new Text();
        private final Text outVal = new Text();
        @Override
        protected void map(LongWritable key, Text value, Context ctx) throws IOException, InterruptedException {
            String[] tok = value.toString().trim().split("\\s+|,");
            if (tok.length < 2) return;
            String u = tok[0], v = tok[1];
            if (u.equals(v)) return;
            int hu = h(u), hv = h(v);
            for (int[] c : combosContaining(rho, hu, hv)) {
                outKey.set(c[0] + "," + c[1] + "," + c[2] + "," + c[3]);
                outVal.set(u + "," + v);
                ctx.write(outKey, outVal);
            }
        }
    }

    public static class PartReducer extends Reducer<Text, Text, Text, DoubleWritable> {
        private int rho;
        @Override
        protected void setup(Context ctx) {
            rho = ctx.getConfiguration().getInt("motif.rho", 8);
        }
        private long comb(int n, int k) {
            if (k < 0 || k > n) return 0;
            if (k == 0 || k == n) return 1;
            long res = 1;
            for (int i = 1; i <= k; i++) res = res * (n - k + i) / i;
            return res;
        }
        private final Text outText = new Text();
        private final DoubleWritable outWeight = new DoubleWritable();

        @Override
        protected void reduce(Text key, Iterable<Text> vals, Context ctx) throws IOException, InterruptedException {
            Map<String, Set<String>> adj = new HashMap<>();
            for (Text t : vals) {
                String[] e = t.toString().split(",", 2);
                if (e.length < 2) continue;
                String u = e[0], v = e[1];
                adj.computeIfAbsent(u, k -> new HashSet<>()).add(v);
                adj.computeIfAbsent(v, k -> new HashSet<>()).add(u);
            }

            for (String x : adj.keySet()) {
                for (String y : adj.get(x)) {
                    if (x.compareTo(y) >= 0) continue;
                    Set<String> common = new HashSet<>(adj.get(x));
                    common.retainAll(adj.get(y));
                    common.remove(x); common.remove(y);
                    if (common.size() < 2) continue;

                    List<String> cList = new ArrayList<>(common);
                    int m = cList.size();
                    for (int i = 0; i < m; i++) {
                        for (int j = i + 1; j < m; j++) {
                            String b = cList.get(i), d = cList.get(j);

                            // Partition weight calculation
                            Set<Integer> parts = new HashSet<>();
                            parts.add(Math.abs(x.hashCode()) % rho);
                            parts.add(Math.abs(b.hashCode()) % rho);
                            parts.add(Math.abs(y.hashCode()) % rho);
                            parts.add(Math.abs(d.hashCode()) % rho);
                            int tsize = parts.size();
                            long z = comb(rho - tsize, 4 - tsize);
                            if (z <= 0) z = 1;
                            double weight = 1.0 / z;

                            // Emit motif breakdown
                            outText.set("MOTIF: " + x + "," + b + "," + y + "," + d);
                            outWeight.set(weight);
                            ctx.write(outText, outWeight);
                        }
                    }
                }
            }
        }
    }

    public static class SumMapper extends Mapper<LongWritable, Text, Text, DoubleWritable> {
        private final static Text ONE = new Text("TOTAL");
        private final DoubleWritable outVal = new DoubleWritable();
        @Override
        protected void map(LongWritable key, Text value, Context ctx) throws IOException, InterruptedException {
            String[] parts = value.toString().trim().split("\\t");
            if (parts.length < 2) return;
            try {
                double w = Double.parseDouble(parts[1]);
                outVal.set(w);
                ctx.write(ONE, outVal);
            } catch (NumberFormatException ignored) {}
        }
    }

    public static class SumReducer extends Reducer<Text, DoubleWritable, Text, DoubleWritable> {
        private final DoubleWritable outVal = new DoubleWritable();
        @Override
        protected void reduce(Text key, Iterable<DoubleWritable> vals, Context ctx) throws IOException, InterruptedException {
            double sum = 0;
            for (DoubleWritable v : vals) sum += v.get();
            outVal.set(sum);
            ctx.write(new Text("Total_Motifs"), outVal);
        }
    }

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
        if (otherArgs.length != 3) {
            System.err.println("Usage: Partition <edges_input> <rho> <output>");
            System.exit(2);
        }

        Path input = new Path(otherArgs[0]);
        int rho = Integer.parseInt(otherArgs[1]);
        Path output = new Path(otherArgs[2]);
        conf.setInt("motif.rho", rho);

        // Job1: partitioned motif computation with breakdown
        Job job1 = Job.getInstance(conf, "PartitionJob");
        job1.setJarByClass(Partition.class);
        job1.setMapperClass(PartMapper.class);
        job1.setReducerClass(PartReducer.class);
        job1.setMapOutputKeyClass(Text.class);
        job1.setMapOutputValueClass(Text.class);
        job1.setOutputKeyClass(Text.class);
        job1.setOutputValueClass(DoubleWritable.class);
        TextInputFormat.addInputPath(job1, input);
        Path tempOut = new Path(output.getParent(), "partition_temp");
        TextOutputFormat.setOutputPath(job1, tempOut);
        if (!job1.waitForCompletion(true)) System.exit(1);

        // Job2: sum weights to get total motif count
        Job job2 = Job.getInstance(conf, "PartitionSum");
        job2.setJarByClass(Partition.class);
        job2.setMapperClass(SumMapper.class);
        job2.setReducerClass(SumReducer.class);
        job2.setMapOutputKeyClass(Text.class);
        job2.setMapOutputValueClass(DoubleWritable.class);
        job2.setOutputKeyClass(Text.class);
        job2.setOutputValueClass(DoubleWritable.class);
        TextInputFormat.addInputPath(job2, tempOut);
        TextOutputFormat.setOutputPath(job2, output);
        System.exit(job2.waitForCompletion(true) ? 0 : 1);
    }
}
