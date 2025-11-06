package week6.rect_cont_join;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter; // <-- Required import
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import java.util.Arrays;

public class rect_cont_Runner {

    // *** CRITICAL PIECE 1: The PathFilter class ***
    // This ensures Job 2 only reads the intermediate data files.
    public static class DefaultOutputFilter implements PathFilter {
        @Override
        public boolean accept(Path path) {
            return path.getName().startsWith("part-r-");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("Usage: rect_cont_Runner <input_path> <output_path> <k> <cell_width> <cell_height>");
            System.err.println("Provided args: " + Arrays.toString(args));
            System.exit(-1);
        }

        Path inputPath = new Path(args[0]);
        Path jobPath = new Path(args[1]); 
        int k = Integer.parseInt(args[2]);
        int cellWidth = Integer.parseInt(args[3]);
        int cellHeight = Integer.parseInt(args[4]);

        Path intermediatePath = new Path(jobPath, "intermediate");
        Path finalOutputPath = new Path(jobPath, "final_output");

        Configuration conf = new Configuration();
        conf.setInt("cell_dim", k);
        conf.setInt("c_width", cellWidth);
        conf.setInt("c_height", cellHeight);

        FileSystem fs = FileSystem.get(conf);
        fs.delete(jobPath, true); // Deletes everything inside the main job directory

        // === JOB 1 ===
        System.out.println("\n=== Starting C-Rep Phase 1: Filter and Mark ===");
        Job job1 = Job.getInstance(conf, "C-Rep Phase 1");
        // ... (Job 1 configuration is correct in your code) ...
        job1.setJarByClass(rect_cont_Runner.class);
        job1.setMapperClass(rect_cont_Mapper1.class);
        job1.setReducerClass(rect_cont_Reducer1.class);
        job1.setNumReduceTasks(k * k);
        job1.setMapOutputKeyClass(Text.class);
        job1.setMapOutputValueClass(Text.class);
        job1.setOutputKeyClass(NullWritable.class);
        job1.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job1, inputPath);
        FileOutputFormat.setOutputPath(job1, intermediatePath);
        MultipleOutputs.addNamedOutput(job1, "finalJoins", TextOutputFormat.class, Text.class, Text.class);

        if (!job1.waitForCompletion(true)) {
            System.err.println("!!! Phase 1 Failed. Aborting. !!!");
            System.exit(1);
        }
        System.out.println("=== Phase 1 Completed Successfully! ===");

        // Ensure intermediate output is visible before starting Job 2 (defensive wait)
        System.out.println("Verifying intermediate path existence: " + intermediatePath);
        int retries = 10;
        while (retries-- > 0) {
            if (fs.exists(intermediatePath)) {
                FileStatus[] parts = fs.listStatus(intermediatePath);
                if (parts != null && parts.length > 0) {
                    break;
                }
            }
            Thread.sleep(500);
        }
        if (!fs.exists(intermediatePath)) {
            throw new RuntimeException("Intermediate path not found after Phase 1: " + intermediatePath);
        }

        // === JOB 2 ===
        System.out.println("\n=== Starting C-Rep Phase 2: Replicate and Join ===");
        Job job2 = Job.getInstance(conf, "C-Rep Phase 2");
        // ... (Job 2 configuration) ...
        job2.setJarByClass(rect_cont_Runner.class);
        job2.setMapperClass(rect_cont_Mapper2.class);
        job2.setReducerClass(rect_cont_Reducer2.class);
        job2.setNumReduceTasks(k * k);
        job2.setMapOutputKeyClass(Text.class);
        job2.setMapOutputValueClass(Text.class);
        job2.setOutputKeyClass(Text.class);
        job2.setOutputValueClass(Text.class);

        // Add only part-r-* files from intermediate as inputs to Job 2
        FileStatus[] partFiles = fs.listStatus(intermediatePath, new PathFilter() {
            @Override
            public boolean accept(Path p) {
                return p.getName().startsWith("part-r-");
            }
        });
        if (partFiles == null || partFiles.length == 0) {
            throw new RuntimeException("No part-r-* files found under: " + intermediatePath);
        }
        for (FileStatus pf : partFiles) {
            FileInputFormat.addInputPath(job2, pf.getPath());
        }
        FileOutputFormat.setOutputPath(job2, finalOutputPath);

        if (!job2.waitForCompletion(true)) {
            System.err.println("\n!!! Phase 2 Failed. !!!");
            System.exit(1);
        }
        System.out.println("=== Phase 2 Completed Successfully! ===");
        
        // === FINAL CLEANUP ===
        // *** CRITICAL PIECE 3: Moving the results from Phase 1 ***
        System.out.println("Moving Phase 1 final results to final output directory...");
        Path finalJoinsPattern = new Path(intermediatePath, "finalJoins-r-*");
        FileStatus[] statuses = fs.globStatus(finalJoinsPattern);
        for (FileStatus status : statuses) {
            fs.rename(status.getPath(), new Path(finalOutputPath, status.getPath().getName()));
        }

        // Now we can safely delete the intermediate directory
        System.out.println("Cleaning up intermediate data at: " + intermediatePath);
        fs.delete(intermediatePath, true);
        
        System.out.println("\n=== Full C-Rep Job Completed Successfully! See results in " + finalOutputPath + " ===");
        System.exit(0);
    }
}