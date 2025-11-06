import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.*;
import java.net.URI;
import java.util.*;


public class SGPACGPAJoin {

    private static List<String> splitCSV(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;
        boolean inQuotes = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                out.add(unquote(cur.toString().trim()));
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(unquote(cur.toString().trim()));
        return out;
    }

    private static String unquote(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static class RegRow {
        String regEventId;
        String studentId;
        String subId;

        RegRow(String regEventId, String studentId, String subId) {
            this.regEventId = regEventId;
            this.studentId = studentId;
            this.subId = subId;
        }
    }

    
    public static class JoinMapper extends Mapper<LongWritable, Text, Text, Text> {

        private final Map<String, RegRow> regByRegId = new HashMap<>();
        private final Map<String, Double> creditsByRegEventAndSub = new HashMap<>();
        private final Map<String, Double> gradePoints = new HashMap<>();
        private String filename;
        private boolean studentGradesHeaderParsed = false;
        private int idxRegId = -1, idxNGrade = -1; 

        @Override
        protected void setup(Context context) throws IOException {
            filename = ((FileSplit) context.getInputSplit()).getPath().getName();

            
            gradePoints.put("EX", 10.0);
            gradePoints.put("A", 9.0);
            gradePoints.put("B", 8.0);
            gradePoints.put("C", 7.0);
            gradePoints.put("D", 6.0);
            gradePoints.put("E", 5.0);
            gradePoints.put("P", 5.0);
            gradePoints.put("F", 0.0);
            gradePoints.put("AB", 0.0);
            gradePoints.put("I", 0.0);


            File taskDir = new File("."); 
            File[] localFiles = taskDir.listFiles();
            if (localFiles == null) localFiles = new File[0];

            File registrationsFile = findLocalized(localFiles, "Registrations.csv");
            File subjectInfoFile = findLocalized(localFiles, "SubjectInfo.csv");

            if (registrationsFile == null || subjectInfoFile == null) {
                throw new IOException("Cache files not found locally. Ensure Registrations.csv and SubjectInfo.csv are added with job.addCacheFile().");
            }

            loadRegistrations(registrationsFile);
            loadSubjectInfo(subjectInfoFile);
        }

        private File findLocalized(File[] files, String nameEndsWith) {
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(nameEndsWith)) return f;
            }

            File f = new File(nameEndsWith);
            return f.exists() ? f : null;
        }

        private void loadRegistrations(File file) throws IOException {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String header = br.readLine(); 
                if (header == null) return;

                List<String> cols = splitCSV(header);
                int idxId = cols.indexOf("id");
                if (idxId < 0) idxId = cols.indexOf("\"id\"");
                int idxRegEventId = cols.indexOf("RegEventId");
                if (idxRegEventId < 0) idxRegEventId = cols.indexOf("\"RegEventId\"");
                int idxStudentId = cols.indexOf("student_id");
                if (idxStudentId < 0) idxStudentId = cols.indexOf("\"student_id\"");
                int idxSubId = cols.indexOf("sub_id");
                if (idxSubId < 0) idxSubId = cols.indexOf("\"sub_id\"");

                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    List<String> p = splitCSV(line);
                    if (p.size() <= Math.max(Math.max(idxId, idxRegEventId), Math.max(idxStudentId, idxSubId))) continue;

                    String id = p.get(idxId);
                    String regEventId = p.get(idxRegEventId);
                    String studentId = p.get(idxStudentId);
                    String subId = p.get(idxSubId);
                    if (id == null || id.isEmpty()) continue;

                    regByRegId.put(id, new RegRow(regEventId, studentId, subId));
                }
            }
        }

        private void loadSubjectInfo(File file) throws IOException {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String header = br.readLine(); 
                if (header == null) return;

                List<String> cols = splitCSV(header);
                int idxSubId = indexOfAny(cols, "SubId", "\"SubId\"");
                int idxCredits = indexOfAny(cols, "Credits", "\"Credits\"");
                int idxRegEventId = indexOfAny(cols, "RegEventId", "\"RegEventId\"");

                if (idxSubId < 0 || idxCredits < 0 || idxRegEventId < 0) {
                    throw new IOException("SubjectInfo.csv header must contain SubId, Credits, RegEventId");
                }

                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    List<String> p = splitCSV(line);
                    if (p.size() <= Math.max(Math.max(idxSubId, idxCredits), idxRegEventId)) continue;

                    String subId = p.get(idxSubId);
                    String regEventId = p.get(idxRegEventId);
                    String creditsStr = p.get(idxCredits);
                    if (subId == null || subId.isEmpty() || regEventId == null || regEventId.isEmpty()) continue;

                    double credits = 0.0;
                    try { credits = Double.parseDouble(creditsStr); } catch (Exception ignored) {}
                    String k = regEventId + "|" + subId;
                    creditsByRegEventAndSub.put(k, credits);
                }
            }
        }

        private int indexOfAny(List<String> cols, String a, String b) {
            int i = cols.indexOf(a);
            if (i >= 0) return i;
            return cols.indexOf(b);
        }

        @Override
        public void map(LongWritable key, Text value, Context ctx) throws IOException, InterruptedException {
            
            if (!filename.toLowerCase().contains("studentgrades")) return;

            String line = value.toString().trim();
            if (line.isEmpty()) return;

            List<String> parts = splitCSV(line);

            
            if (!studentGradesHeaderParsed) {
                
                int idxIdH = parts.indexOf("id");
                int idxRegIdH = parts.indexOf("RegId");
                int idxNGradeH = parts.indexOf("NGrade");

                
                if (idxIdH >= 0 || idxRegIdH >= 0 || idxNGradeH >= 0) {
                    idxRegId = idxRegIdH;
                    idxNGrade = idxNGradeH;
                    studentGradesHeaderParsed = true;
                    return;
                }

                
                
                if (idxRegId < 0 || idxNGrade < 0) {
                    
                    idxRegId = 4;
                    idxNGrade = 5;
                    studentGradesHeaderParsed = true;
                }
            }

            
            if (parts.size() <= Math.max(idxRegId, idxNGrade)) return;

            String regId = parts.get(idxRegId);
            String ngrade = parts.get(idxNGrade);
            if (regId == null || regId.isEmpty()) return;

            RegRow rr = regByRegId.get(regId);
            if (rr == null) {
                
                return;
            }

            
            double gp = 0.0;
            if (ngrade != null) {
                Double gpObj = gradePoints.get(ngrade.toUpperCase());
                gp = (gpObj == null) ? 0.0 : gpObj;
            }

            
            String keyCredits = rr.regEventId + "|" + rr.subId;
            Double creditsObj = creditsByRegEventAndSub.get(keyCredits);
            double credits = (creditsObj == null) ? 0.0 : creditsObj;

            if (credits <= 0.0) {
                
                return;
            }

            double weighted = gp * credits;

            
            
            String v = "C:" + rr.regEventId + ":" + credits + ":" + gp + ":" + weighted;
            ctx.write(new Text(rr.studentId), new Text(v));
        }
    }

    
    public static class SGPAReducer extends Reducer<Text, Text, NullWritable, Text> {

        private final Text out = new Text();
        private boolean headerWritten = false;

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            
        }

        @Override
        public void reduce(Text studentId, Iterable<Text> values, Context ctx) throws IOException, InterruptedException {
            
            Map<String, double[]> semAgg = new HashMap<>();

            for (Text t : values) {
                String s = t.toString();
                
                if (!s.startsWith("C:")) continue;
                String[] p = s.split(":", -1);
                if (p.length < 5) continue;

                String regEventId = p[1];
                double credits = parseDoubleSafe(p[2]);
                double weighted = parseDoubleSafe(p[4]);

                double[] agg = semAgg.get(regEventId);
                if (agg == null) {
                    agg = new double[] {0.0, 0.0}; 
                    semAgg.put(regEventId, agg);
                }
                agg[0] += weighted;
                agg[1] += credits;
            }

            if (semAgg.isEmpty()) return;

            
            if (!headerWritten) {
                out.set("student_id,RegEventId,semester_credits,SGPA,CGPA_to_date");
                ctx.write(NullWritable.get(), out);
                headerWritten = true;
            }

            
            List<String> regs = new ArrayList<>(semAgg.keySet());
            regs.sort((a, b) -> {
                try {
                    return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
                } catch (Exception e) {
                    return a.compareTo(b);
                }
            });

            double totalWeightedAll = 0.0;
            double totalCreditsAll = 0.0;

            for (String reg : regs) {
                double[] agg = semAgg.get(reg);
                double semWeighted = agg[0];
                double semCredits = agg[1];
                if (semCredits <= 0) continue;

                double sgpa = semWeighted / semCredits;

                
                totalWeightedAll += semWeighted;
                totalCreditsAll += semCredits;
                double cgpa = (totalCreditsAll > 0) ? (totalWeightedAll / totalCreditsAll) : 0.0;

                out.set(studentId.toString() + "," + reg + "," +
                        round2(semCredits) + "," + round2(sgpa) + "," + round2(cgpa));
                ctx.write(NullWritable.get(), out);
            }
        }

        private double parseDoubleSafe(String s) {
            try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
        }

        private String round2(double d) {
            return String.format(java.util.Locale.US, "%.2f", d);
        }
    }

    
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: SGPACGPAJoin <inputDir> <outputDir>");
            System.exit(1);
        }
        String inputDir = args[0];
        String outputDir = args[1];

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "SGPA_CGPA_Join");
        job.setJarByClass(SGPACGPAJoin.class);

        
        job.setMapperClass(JoinMapper.class);
        job.setReducerClass(SGPAReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);

        
        job.setNumReduceTasks(1);

        
        FileInputFormat.addInputPath(job, new Path(inputDir));

        
        FileOutputFormat.setOutputPath(job, new Path(outputDir));

        
        job.addCacheFile(new URI(inputDir + (inputDir.endsWith("/") ? "" : "/") + "Registrations.csv"));
        job.addCacheFile(new URI(inputDir + (inputDir.endsWith("/") ? "" : "/") + "SubjectInfo.csv"));

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
