import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class MultiwayJoin {

    /** 
     * TaggedTuple wraps a tuple with its source relation (R, S, or T).
     */
    public static class TaggedTuple implements Writable {
        private Text relationTag;
        private Text tupleData;

        public TaggedTuple() {
            this.relationTag = new Text();
            this.tupleData = new Text();
        }

        public TaggedTuple(String tag, String data) {
            this.relationTag = new Text(tag);
            this.tupleData = new Text(data);
        }

        @Override
        public void write(DataOutput out) throws IOException {
            relationTag.write(out);
            tupleData.write(out);
        }

        @Override
        public void readFields(DataInput in) throws IOException {
            relationTag.readFields(in);
            tupleData.readFields(in);
        }

        public String getTag() { return relationTag.toString(); }
        public String getData() { return tupleData.toString(); }
    }

    /**
     * Composite key used for partitioning on hashed values of attributes B and C.
     */
    public static class CompositeKey implements WritableComparable<CompositeKey> {
        private IntWritable bHash = new IntWritable();
        private IntWritable cHash = new IntWritable();

        public CompositeKey() {}

        public CompositeKey(int b, int c) {
            bHash.set(b);
            cHash.set(c);
        }

        @Override
        public void write(DataOutput out) throws IOException {
            bHash.write(out);
            cHash.write(out);
        }

        @Override
        public void readFields(DataInput in) throws IOException {
            bHash.readFields(in);
            cHash.readFields(in);
        }

        @Override
        public int compareTo(CompositeKey other) {
            int cmp = bHash.compareTo(other.bHash);
            return (cmp != 0) ? cmp : cHash.compareTo(other.cHash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bHash.get(), cHash.get());
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CompositeKey)) return false;
            CompositeKey other = (CompositeKey) obj;
            return bHash.equals(other.bHash) && cHash.equals(other.cHash);
        }
    }

    /**
     * Mapper that distributes tuples from R, S, and T into hash buckets.
     * 
     * - R tuples: replicate across all C buckets.
     * - S tuples: single (B, C) bucket.
     * - T tuples: replicate across all B buckets.
     */
    public static class JoinMapper extends Mapper<LongWritable, Text, CompositeKey, TaggedTuple> {
        private static final int NUM_BUCKETS = 4; // adjustable

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] fields = value.toString().split(",");
            String relation = fields[0];

            if ("R".equals(relation) && fields.length >= 3) {
                int bBucket = Math.abs(fields[2].hashCode()) % NUM_BUCKETS;
                for (int c = 0; c < NUM_BUCKETS; c++) {
                    context.write(new CompositeKey(bBucket, c), new TaggedTuple("R", fields[1] + "," + fields[2]));
                }

            } else if ("S".equals(relation) && fields.length >= 3) {
                int bBucket = Math.abs(fields[1].hashCode()) % NUM_BUCKETS;
                int cBucket = Math.abs(fields[2].hashCode()) % NUM_BUCKETS;
                context.write(new CompositeKey(bBucket, cBucket), new TaggedTuple("S", fields[1] + "," + fields[2]));

            } else if ("T".equals(relation) && fields.length >= 3) {
                int cBucket = Math.abs(fields[1].hashCode()) % NUM_BUCKETS;
                for (int b = 0; b < NUM_BUCKETS; b++) {
                    context.write(new CompositeKey(b, cBucket), new TaggedTuple("T", fields[1] + "," + fields[2]));
                }
            }
        }
    }

    /**
     * Custom partitioner to assign (B, C) keys to reducers.
     */
    public static class CompositePartitioner extends Partitioner<CompositeKey, TaggedTuple> {
        @Override
        public int getPartition(CompositeKey key, TaggedTuple value, int numPartitions) {
            return (key.hashCode() & Integer.MAX_VALUE) % numPartitions;
        }
    }

    /**
     * Reducer performs the actual join:
     *   For each (b, c) bucket:
     *     Combine R, S, and T tuples into (a, b, c, d).
     */
    public static class JoinReducer extends Reducer<CompositeKey, TaggedTuple, NullWritable, Text> {
        @Override
        protected void reduce(CompositeKey key, Iterable<TaggedTuple> values, Context context)
                throws IOException, InterruptedException {
            
            List<String> relationR = new ArrayList<>();
            List<String> relationS = new ArrayList<>();
            List<String> relationT = new ArrayList<>();

            // Separate tuples by relation type
            for (TaggedTuple tuple : values) {
                switch (tuple.getTag()) {
                    case "R": relationR.add(tuple.getData()); break;
                    case "S": relationS.add(tuple.getData()); break;
                    case "T": relationT.add(tuple.getData()); break;
                }
            }

            // Perform join: R ⋈ S ⋈ T
            for (String sTuple : relationS) {
                String[] bc = sTuple.split(",");
                String b = bc[0];
                String c = bc[1];

                for (String rTuple : relationR) {
                    String[] ab = rTuple.split(",");
                    String a = ab[0];

                    for (String tTuple : relationT) {
                        String[] cd = tTuple.split(",");
                        String d = cd[1];
                        context.write(NullWritable.get(), new Text(a + "," + b + "," + c + "," + d));
                    }
                }
            }
        }
    }

    /**
     * Driver: Configures and launches the MapReduce job.
     */
    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "file:///");
        conf.set("mapreduce.framework.name", "local");

        String[] paths = new GenericOptionsParser(conf, args).getRemainingArgs();
        if (paths.length != 4) {
            System.err.println("Usage: MultiwayJoin <R> <S> <T> <output>");
            System.exit(-1);
        }

        Job job = Job.getInstance(conf, "MultiwayJoin");
        job.setJarByClass(MultiwayJoin.class);

        job.setMapperClass(JoinMapper.class);
        job.setPartitionerClass(CompositePartitioner.class);
        job.setReducerClass(JoinReducer.class);

        job.setMapOutputKeyClass(CompositeKey.class);
        job.setMapOutputValueClass(TaggedTuple.class);

        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);

        MultipleInputs.addInputPath(job, new Path(paths[0]), TextInputFormat.class, JoinMapper.class);
        MultipleInputs.addInputPath(job, new Path(paths[1]), TextInputFormat.class, JoinMapper.class);
        MultipleInputs.addInputPath(job, new Path(paths[2]), TextInputFormat.class, JoinMapper.class);
        FileOutputFormat.setOutputPath(job, new Path(paths[3]));

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
