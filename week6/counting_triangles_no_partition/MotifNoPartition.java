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
 * MotifNoPartition
 * Counts 4-node motifs in an undirected graph,
 * outputs total count and breakdown of included nodes.
 */
public class MotifNoPartition {

    // ---------- Job1: Build adjacency lists ----------
    public static class AdjBuilderMapper extends Mapper<LongWritable, Text, Text, Text> {
        private Text outKey = new Text();
        private Text outVal = new Text();
        @Override
        protected void map(LongWritable key, Text value, Context ctx) throws IOException, InterruptedException {
            String line = value.toString().trim();
            if (line.isEmpty() || line.startsWith("#")) return;
            String[] toks = line.split("\\s+|,");
            if (toks.length < 2) return;
            String u = toks[0];
            String v = toks[1];
            outKey.set(u); outVal.set(v); ctx.write(outKey, outVal);
            outKey.set(v); outVal.set(u); ctx.write(outKey, outVal);
        }
    }

    public static class AdjBuilderReducer extends Reducer<Text, Text, Text, Text> {
        private Text outVal = new Text();
        @Override
        protected void reduce(Text key, Iterable<Text> vals, Context ctx) throws IOException, InterruptedException {
            Set<String> nbrs = new HashSet<>();
            for (Text t : vals) nbrs.add(t.toString());
            List<String> list = new ArrayList<>(nbrs);
            Collections.sort(list);
            outVal.set(String.join(",", list));
            ctx.write(key, outVal);
        }
    }

    // ---------- Job2: Count motifs with breakdown ----------
    public static class PairEmitMapper extends Mapper<LongWritable, Text, Text, Text> {
        private Text outKey = new Text();
        private Text outVal = new Text();
        @Override
        protected void map(LongWritable key, Text value, Context ctx) throws IOException, InterruptedException {
            String line = value.toString().trim();
            if (line.isEmpty()) return;
            String[] parts = line.split("\\t");
            if (parts.length < 2) return;
            String node = parts[0];
            String neighbors = parts[1];
            if (neighbors.trim().isEmpty()) return;
            String[] nbrs = neighbors.split(",");
            for (String nbr : nbrs) {
                if (nbr.isEmpty()) continue;
                String a = node;
                String b = nbr;
                if (a.equals(b)) continue;
                String keyEdge = (a.compareTo(b) < 0) ? a + "\t" + b : b + "\t" + a;
                outKey.set(keyEdge);
                outVal.set("N|" + node + "|" + neighbors);
                ctx.write(outKey, outVal);
            }
        }
    }

    public static class PairJoinReducer extends Reducer<Text, Text, Text, LongWritable> {
        private long motifCount = 0;
        private Text outText = new Text();

        @Override
        protected void reduce(Text key, Iterable<Text> vals, Context ctx) throws IOException, InterruptedException {
            String[] nodes = key.toString().split("\\t");
            if (nodes.length != 2) return;
            String u = nodes[0], v = nodes[1];

            List<String> listA = null, listB = null;
            for (Text t : vals) {
                String s = t.toString();
                String[] p = s.split("\\|", 3);
                if (p.length < 3) continue;
                String owner = p[1];
                List<String> list = Arrays.asList(p[2].split(","));
                if (owner.equals(u)) listA = new ArrayList<>(list);
                else if (owner.equals(v)) listB = new ArrayList<>(list);
                else {
                    if (listA == null) listA = new ArrayList<>(list);
                    else if (listB == null) listB = new ArrayList<>(list);
                }
            }

            if (listA == null || listB == null) return;

            Set<String> sa = new HashSet<>(listA);
            Set<String> sb = new HashSet<>(listB);
            sa.retainAll(sb);
            sa.remove(u); sa.remove(v);

            if (sa.size() < 2) return;

            List<String> common = new ArrayList<>(sa);
            int n = common.size();
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    String b = common.get(i);
                    String d = common.get(j);

                    // Canonical ordering of the motif
                    List<String> motifNodes = Arrays.asList(u, v, b, d);
                    Collections.sort(motifNodes); 
                    String motifKey = String.join(",", motifNodes);

                    motifCount++;
                    outText.set(motifKey);
                    ctx.write(outText, new LongWritable(1));
                }
            }
        }

        @Override
        protected void cleanup(Context ctx) throws IOException, InterruptedException {
            ctx.write(new Text("Total_Motifs"), new LongWritable(motifCount));
        }
    }

    // ---------- Driver ----------
    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
        if (otherArgs.length != 3) {
            System.err.println("Usage: MotifNoPartition <edges_input> <temp_adj_output> <final_output>");
            System.exit(2);
        }
        Path edgesIn = new Path(otherArgs[0]);
        Path adjOut = new Path(otherArgs[1]);
        Path finalOut = new Path(otherArgs[2]);

        // Job1: adjacency builder
        Job job1 = Job.getInstance(conf, "AdjBuilder");
        job1.setJarByClass(MotifNoPartition.class);
        job1.setMapperClass(AdjBuilderMapper.class);
        job1.setReducerClass(AdjBuilderReducer.class);
        job1.setOutputKeyClass(Text.class);
        job1.setOutputValueClass(Text.class);
        TextInputFormat.addInputPath(job1, edgesIn);
        TextOutputFormat.setOutputPath(job1, adjOut);
        boolean ok = job1.waitForCompletion(true);
        if (!ok) System.exit(1);

        // Job2: motif counting
        Job job2 = Job.getInstance(conf, "MotifCount");
        job2.setJarByClass(MotifNoPartition.class);
        job2.setMapperClass(PairEmitMapper.class);
        job2.setReducerClass(PairJoinReducer.class);
        job2.setMapOutputKeyClass(Text.class);
        job2.setMapOutputValueClass(Text.class);
        job2.setOutputKeyClass(Text.class);
        job2.setOutputValueClass(LongWritable.class);
        TextInputFormat.addInputPath(job2, adjOut);
        TextOutputFormat.setOutputPath(job2, finalOut);
        System.exit(job2.waitForCompletion(true) ? 0 : 1);
    }
}
