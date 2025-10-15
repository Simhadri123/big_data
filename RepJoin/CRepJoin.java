import java.io.IOException;
import java.util.*;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class CRepJoin {

  // ---------- Basic data structures ----------
  public static class Rect {
    public String rel; // P, Q, R, or S
    public String id;
    public double xmin, ymin, xmax, ymax;
    public Rect() {}
    public Rect(String rel, String id, double xmin, double ymin, double xmax, double ymax) {
      this.rel = rel; this.id = id; this.xmin = xmin; this.ymin = ymin; this.xmax = xmax; this.ymax = ymax;
    }
    public String serialize() {
      return rel + "|" + id + "|" + xmin + "|" + ymin + "|" + xmax + "|" + ymax;
    }
    public static Rect deserialize(String s) {
      String[] a = s.split("\\|");
      return new Rect(a[0], a[1], Double.parseDouble(a[2]), Double.parseDouble(a[3]),
                      Double.parseDouble(a[4]), Double.parseDouble(a[5]));
    }
  }

  // utilities: check overlap and boundary crossing
  public static boolean overlaps(Rect a, Rect b) {
    return !(a.xmax < b.xmin || a.xmin > b.xmax || a.ymax < b.ymin || a.ymin > b.ymax);
  }
  // whether rectangle crosses the boundary of the cell (cellBBox given)
  public static boolean crossesCellBoundary(Rect r, double cellXmin, double cellYmin, double cellXmax, double cellYmax) {
    // If r fully inside cell => false, else true
    return !(r.xmin >= cellXmin && r.ymin >= cellYmin && r.xmax <= cellXmax && r.ymax <= cellYmax);
  }

  // split rectangle to list of cellIds it intersects
  public static List<String> splitToCells(Rect r, double bboxXmin, double bboxYmin, double bboxXmax, double bboxYmax,
                                          int numRows, int numCols) {
    // compute grid cell width/height
    double cellW = (bboxXmax - bboxXmin) / numCols;
    double cellH = (bboxYmax - bboxYmin) / numRows;
    int colStart = (int)Math.floor((r.xmin - bboxXmin) / cellW);
    int colEnd   = (int)Math.floor((r.xmax - bboxXmin) / cellW);
    int rowStart = (int)Math.floor((r.ymin - bboxYmin) / cellH);
    int rowEnd   = (int)Math.floor((r.ymax - bboxYmin) / cellH);
    // clamp
    colStart = Math.max(0, Math.min(numCols-1, colStart));
    colEnd   = Math.max(0, Math.min(numCols-1, colEnd));
    rowStart = Math.max(0, Math.min(numRows-1, rowStart));
    rowEnd   = Math.max(0, Math.min(numRows-1, rowEnd));
    List<String> cells = new ArrayList<>();
    for (int rIdx = rowStart; rIdx <= rowEnd; ++rIdx) {
      for (int cIdx = colStart; cIdx <= colEnd; ++cIdx) {
        cells.add(rIdx + "_" + cIdx);
      }
    }
    return cells;
  }


  // ---------- PHASE 1 Mapper ----------
  public static class Phase1Mapper extends Mapper<LongWritable, Text, Text, Text> {
    double bboxXmin, bboxYmin, bboxXmax, bboxYmax;
    int numRows, numCols;
    @Override
    protected void setup(Context ctx) {
      Configuration c = ctx.getConfiguration();
      bboxXmin = c.getDouble("bbox.xmin", 0.0);
      bboxYmin = c.getDouble("bbox.ymin", 0.0);
      bboxXmax = c.getDouble("bbox.xmax", 100000.0);
      bboxYmax = c.getDouble("bbox.ymax", 100000.0);
      numRows = c.getInt("grid.rows", 8);
      numCols = c.getInt("grid.cols", 8);
    }
    @Override
    protected void map(LongWritable key, Text value, Context ctx) throws IOException, InterruptedException {
      // input: rel,id,xmin,ymin,xmax,ymax
      String[] parts = value.toString().split(",");
      if (parts.length < 6) return;
      Rect r = new Rect(parts[0].trim(), parts[1].trim(),
                        Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                        Double.parseDouble(parts[4]), Double.parseDouble(parts[5]));
      List<String> cells = splitToCells(r, bboxXmin, bboxYmin, bboxXmax, bboxYmax, numRows, numCols);
      for (String cell : cells) {
        // Key = cellId, Value = "RECT|serializedRect"
        ctx.write(new Text(cell), new Text("RECT|" + r.serialize()));
      }
    }
  }

  // ---------- PHASE 1 Reducer ----------
  public static class Phase1Reducer extends Reducer<Text, Text, NullWritable, Text> {
    double bboxXmin, bboxYmin, bboxXmax, bboxYmax;
    int numRows, numCols;
    // We'll collect lists by relation
    @Override
    protected void setup(Context ctx) {
      Configuration c = ctx.getConfiguration();
      bboxXmin = c.getDouble("bbox.xmin", 0.0);
      bboxYmin = c.getDouble("bbox.ymin", 0.0);
      bboxXmax = c.getDouble("bbox.xmax", 100000.0);
      bboxYmax = c.getDouble("bbox.ymax", 100000.0);
      numRows = c.getInt("grid.rows", 8);
      numCols = c.getInt("grid.cols", 8);
    }
    @Override
    protected void reduce(Text key, Iterable<Text> values, Context ctx) throws IOException, InterruptedException {
      // cell bounding box:
      String[] rc = key.toString().split("_");
      int row = Integer.parseInt(rc[0]), col = Integer.parseInt(rc[1]);
      double cellW = (bboxXmax - bboxXmin) / numCols;
      double cellH = (bboxYmax - bboxYmin) / numRows;
      double cellXmin = bboxXmin + col * cellW;
      double cellYmin = bboxYmin + row * cellH;
      double cellXmax = cellXmin + cellW;
      double cellYmax = cellYmin + cellH;

      List<Rect> P = new ArrayList<>(), Q = new ArrayList<>(), R = new ArrayList<>(), S = new ArrayList<>();

      // parse incoming rects
      for (Text t : values) {
        String v = t.toString();
        if (!v.startsWith("RECT|")) continue;
        Rect r = Rect.deserialize(v.substring(5));
        switch (r.rel) {
          case "P": P.add(r); break;
          case "Q": Q.add(r); break;
          case "R": R.add(r); break;
          case "S": S.add(r); break;
        }
      }

      // We'll produce:
      // 1) final safe outputs for q-r pairs fully internal
      // 2) a list of rectangles to be replicated (emit as special records)
      Set<String> markedForReplication = new HashSet<>(); // keep id+rel to avoid duplicates in this reducer

      // nested loop for q-r
      for (Rect q : Q) {
        for (Rect r : R) {
          if (!overlaps(q, r)) continue;
          boolean qCross = crossesCellBoundary(q, cellXmin, cellYmin, cellXmax, cellYmax);
          boolean rCross = crossesCellBoundary(r, cellXmin, cellYmin, cellXmax, cellYmax);
          if (!qCross && !rCross) {
            // both inside cell -> we can produce final tuples for all p overlapping q and s overlapping r
            for (Rect p : P) {
              if (!overlaps(p, q)) continue;
              for (Rect s : S) {
                if (!overlaps(r, s)) continue;
                // emit final tuple: p,q,r,s
                String out = String.format("OUT|%s|%s|%s|%s", p.serialize(), q.serialize(), r.serialize(), s.serialize());
                ctx.write(NullWritable.get(), new Text(out));
              }
            }
          } else {
            // mark appropriate rectangles for replication.
            // If q or r crosses boundary then p that overlaps q and s that overlaps r must be marked too
            // Mark q and r
            String qkey = "Q|" + q.id;
            if (!markedForReplication.contains(qkey) && Project(q, cellXmin, cellYmin, cellXmax, cellYmax))
              markedForReplication.add(qkey);
            String rkey = "R|" + r.id;
            if (!markedForReplication.contains(rkey) && Project(r, cellXmin, cellYmin, cellXmax, cellYmax))
              markedForReplication.add(rkey);
            // mark p's overlapping q
            for (Rect p : P) if (overlaps(p,q)) {
              String pk = "P|" + p.id;
              if (!markedForReplication.contains(pk) && Project(p, cellXmin, cellYmin, cellXmax, cellYmax))
                markedForReplication.add(pk);
            }
            // mark s's overlapping r
            for (Rect s : S) if (overlaps(s,r)) {
              String sk = "S|" + s.id;
              if (!markedForReplication.contains(sk) && Project(s, cellXmin, cellYmin, cellXmax, cellYmax))
                markedForReplication.add(sk);
            }
          }
        }
      }

      // Emit marked rectangles for replication to Phase2 via special marker records
      // We must emit the rectangle whole record; to avoid duplication across reducers when rectangle intersects many cells, we used Project() above (returns true if lb(r) lies in current cell)
      for (Rect p : P) {
        String keyMark = "P|" + p.id;
        if (markedForReplication.contains(keyMark)) {
          ctx.write(NullWritable.get(), new Text("MARK|" + p.serialize()));
        }
      }
      for (Rect q : Q) {
        String keyMark = "Q|" + q.id;
        if (markedForReplication.contains(keyMark)) {
          ctx.write(NullWritable.get(), new Text("MARK|" + q.serialize()));
        }
      }
      for (Rect r : R) {
        String keyMark = "R|" + r.id;
        if (markedForReplication.contains(keyMark)) {
          ctx.write(NullWritable.get(), new Text("MARK|" + r.serialize()));
        }
      }
      for (Rect s : S) {
        String keyMark = "S|" + s.id;
        if (markedForReplication.contains(keyMark)) {
          ctx.write(NullWritable.get(), new Text("MARK|" + s.serialize()));
        }
      }
    } // reduce

    // Project: returns true if lower-left corner (lb) of rectangle lies in this cell
    private static boolean Project(Rect r, double cellXmin, double cellYmin, double cellXmax, double cellYmax) {
      double lx = r.xmin, ly = r.ymin;
      return (lx >= cellXmin && lx < cellXmax && ly >= cellYmin && ly < cellYmax);
    }
  }

  // ---------- PHASE 2 Mapper ----------
  // Input is the MARK|rect or possibly also original rectangles we choose to replicate
  public static class Phase2Mapper extends Mapper<LongWritable, Text, Text, Text> {
    double bboxXmin, bboxYmin, bboxXmax, bboxYmax;
    int numRows, numCols;
    @Override
    protected void setup(Context ctx) {
      Configuration c = ctx.getConfiguration();
      bboxXmin = c.getDouble("bbox.xmin", 0.0);
      bboxYmin = c.getDouble("bbox.ymin", 0.0);
      bboxXmax = c.getDouble("bbox.xmax", 100000.0);
      bboxYmax = c.getDouble("bbox.ymax", 100000.0);
      numRows = c.getInt("grid.rows", 8);
      numCols = c.getInt("grid.cols", 8);
    }
    @Override
    protected void map(LongWritable key, Text value, Context ctx) throws IOException, InterruptedException {
      String line = value.toString();
      if (!line.startsWith("MARK|")) return;
      Rect r = Rect.deserialize(line.substring(5));
      // C-Rep replicates boundary items appropriately (for simplicity: replicate to first-quadrant like All-Replicate but only for marked items)
      // Implemented here: for a rectangle r, compute Cf(r) (cells with row >= Row(lb) and col >= Col(lb))
      double cellW = (bboxXmax - bboxXmin) / numCols;
      double cellH = (bboxYmax - bboxYmin) / numRows;
      int col = (int)Math.floor((r.xmin - bboxXmin) / cellW);
      int row = (int)Math.floor((r.ymin - bboxYmin) / cellH);
      col = Math.max(0, Math.min(numCols-1, col));
      row = Math.max(0, Math.min(numRows-1, row));
      for (int rr = row; rr < numRows; ++rr) {
        for (int cc = col; cc < numCols; ++cc) {
          String cell = rr + "_" + cc;
          // emit to Phase2 reducer keyed by cell
          ctx.write(new Text(cell), new Text("RECT|" + r.serialize()));
        }
      }
    }
  }

  // ---------- PHASE 2 Reducer ----------
  public static class Phase2Reducer extends Reducer<Text, Text, NullWritable, Text> {
    @Override
    protected void reduce(Text key, Iterable<Text> values, Context ctx) throws IOException, InterruptedException {
      List<Rect> P = new ArrayList<>(), Q = new ArrayList<>(), R = new ArrayList<>(), S = new ArrayList<>();
      for (Text t : values) {
        String v = t.toString();
        if (!v.startsWith("RECT|")) continue;
        Rect r = Rect.deserialize(v.substring(5));
        switch (r.rel) {
          case "P": P.add(r); break;
          case "Q": Q.add(r); break;
          case "R": R.add(r); break;
          case "S": S.add(r); break;
        }
      }

      // Now nested-loop final join among P,Q,R,S
      for (Rect q : Q) {
        for (Rect r : R) {
          if (!overlaps(q, r)) continue;
          for (Rect p : P) {
            if (!overlaps(p, q)) continue;
            for (Rect s : S) {
              if (!overlaps(r, s)) continue;
              String out = String.format("OUT|%s|%s|%s|%s", p.serialize(), q.serialize(), r.serialize(), s.serialize());
              ctx.write(NullWritable.get(), new Text(out));
            }
          }
        }
      }
    }
  }

  // ---------- Main Driver ----------
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("Usage: CRepJoin <input> <output>");
      System.exit(1);
    }

    Configuration conf = new Configuration();
    
    // Set spatial parameters
    conf.setDouble("bbox.xmin", 0.0);
    conf.setDouble("bbox.ymin", 0.0);
    conf.setDouble("bbox.xmax", 100000.0);
    conf.setDouble("bbox.ymax", 100000.0);
    conf.setInt("grid.rows", 8);
    conf.setInt("grid.cols", 8);

    // Phase 1 Job
    Job job1 = Job.getInstance(conf, "CRepJoin-Phase1");
    job1.setJarByClass(CRepJoin.class);
    job1.setMapperClass(Phase1Mapper.class);
    job1.setReducerClass(Phase1Reducer.class);
    job1.setOutputKeyClass(Text.class);
    job1.setOutputValueClass(Text.class);

    FileInputFormat.addInputPath(job1, new Path(args[0]));
    Path phase1Output = new Path(args[1] + "_phase1");
    FileOutputFormat.setOutputPath(job1, phase1Output);

    if (!job1.waitForCompletion(true)) {
      System.exit(1);
    }

    // Phase 2 Job
    Job job2 = Job.getInstance(conf, "CRepJoin-Phase2");
    job2.setJarByClass(CRepJoin.class);
    job2.setMapperClass(Phase2Mapper.class);
    job2.setReducerClass(Phase2Reducer.class);
    job2.setOutputKeyClass(Text.class);
    job2.setOutputValueClass(Text.class);

    FileInputFormat.addInputPath(job2, phase1Output);
    FileOutputFormat.setOutputPath(job2, new Path(args[1]));

    System.exit(job2.waitForCompletion(true) ? 0 : 1);
  }

} // end CRepJoin
