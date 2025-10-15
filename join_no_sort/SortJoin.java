import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 * Join using sorting without explicitly sorting.
 * Based on Data Algorithms book example.
 */
public class SortJoin {

    /** Tagged tuple to distinguish between R and S relations */
    public static class TaggedTuple implements Writable {
        private Text relationTag = new Text();
        private Text tupleData = new Text();

        public TaggedTuple() {}
        public TaggedTuple(String tag, String data) {
            this.relationTag.set(tag);
            this.tupleData.set(data);
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

    /** Composite key = (joinKey, sourceTag) */
    public static class CompositeKey implements WritableComparable<CompositeKey> {
        private Text joinKey = new Text();
        private Text sourceTag = new Text(); // ensures ordering: R before S

        public CompositeKey() {}
        public CompositeKey(String key, String tag) {
            this.joinKey.set(key);
            this.sourceTag.set(tag);
        }

        @Override
        public void write(DataOutput out) throws IOException {
            joinKey.write(out);
            sourceTag.write(out);
        }

        @Override
        public void readFields(DataInput in) throws IOException {
            joinKey.readFields(in);
            sourceTag.readFields(in);
        }

        @Override
        public int compareTo(CompositeKey other) {
            int cmp = joinKey.compareTo(other.joinKey);
            if (cmp != 0) return cmp;
            return sourceTag.compareTo(other.sourceTag);
        }

        public String getJoinKey() { return joinKey.toString(); }
        public String getSourceTag() { return sourceTag.toString(); }
    }

    /** Mapper for R relation */
    public static class RMapper extends Mapper<LongWritable, Text, CompositeKey, TaggedTuple> {
        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] parts = value.toString().split(",");
            if (parts[0].equals("R")) {
                String a = parts[1];
                String b = parts[2];
                context.write(new CompositeKey(b, "R"), new TaggedTuple("R", a + "," + b));
            }
        }
    }

    /** Mapper for S relation */
    public static class SMapper extends Mapper<LongWritable, Text, CompositeKey, TaggedTuple> {
        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] parts = value.toString().split(",");
            if (parts[0].equals("S")) {
                String b = parts[1];
                String c = parts[2];
                context.write(new CompositeKey(b, "S"), new TaggedTuple("S", b + "," + c));
            }
        }
    }

    /** Partitioner: partition only by joinKey */
    public static class JoinPartitioner extends Partitioner<CompositeKey, TaggedTuple> {
        @Override
        public int getPartition(CompositeKey key, TaggedTuple value, int numPartitions) {
            return (key.getJoinKey().hashCode() & Integer.MAX_VALUE) % numPartitions;
        }
    }

    /** Grouping comparator: group only by joinKey */
    public static class JoinGroupingComparator extends WritableComparator {
        protected JoinGroupingComparator() {
            super(CompositeKey.class, true);
        }

        @Override
        public int compare(WritableComparable w1, WritableComparable w2) {
            CompositeKey k1 = (CompositeKey) w1;
            CompositeKey k2 = (CompositeKey) w2;
            return k1.getJoinKey().compareTo(k2.getJoinKey());
        }
    }

    /** Reducer: first R records, then S records */
    public static class JoinReducer extends Reducer<CompositeKey, TaggedTuple, NullWritable, Text> {
        @Override
        protected void reduce(CompositeKey key, Iterable<TaggedTuple> values, Context context)
                throws IOException, InterruptedException {
            String leftVal = null;
            for (TaggedTuple tuple : values) {
                if (tuple.getTag().equals("R")) {
                    leftVal = tuple.getData(); // A,B
                } else if (tuple.getTag().equals("S") && leftVal != null) {
                    String[] ab = leftVal.split(",");
                    String a = ab[0];
                    String b = ab[1];
                    String[] bc = tuple.getData().split(",");
                    String c = bc[1];
                    context.write(NullWritable.get(), new Text(a + "," + b + "," + c));
                }
            }
        }
    }

    /** Driver */
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: SortJoin <R> <S> <out>");
            System.exit(2);
        }

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "SortMergeJoin");
        job.setJarByClass(SortJoin.class);

        MultipleInputs.addInputPath(job, new Path(args[0]), TextInputFormat.class, RMapper.class);
        MultipleInputs.addInputPath(job, new Path(args[1]), TextInputFormat.class, SMapper.class);

        job.setMapOutputKeyClass(CompositeKey.class);
        job.setMapOutputValueClass(TaggedTuple.class);

        job.setPartitionerClass(JoinPartitioner.class);
        job.setGroupingComparatorClass(JoinGroupingComparator.class);

        job.setReducerClass(JoinReducer.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);

        FileOutputFormat.setOutputPath(job, new Path(args[2]));

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
