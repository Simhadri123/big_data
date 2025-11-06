package week6.rect_cont_join;

import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.conf.Configuration;

import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import java.io.IOException;
import java.util.*;

public class rect_cont_Reducer1 extends Reducer<Text, Text, NullWritable, Text> {
    
    private int k; // Grid dimension (k x k)
    private int cell_width;
    private int cell_height;
    
    private Set<Rectangle> Marked;

    private MultipleOutputs<NullWritable, Text> mos;
    
    private static class Rectangle {
        int x1, y1, x2, y2;
        String type;
        
        Rectangle(int x1, int y1, int x2, int y2, String type) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.type = type;
        }
        
        @Override
        public String toString() {
            return String.format("(%d,%d,%d,%d)", x1, y1, x2, y2);
        }

        public String toInputString() {
            return String.format("%d %d %d %d %s", x1, y1, x2, y2, type);
        }

        @Override
        public int hashCode(){
            return Objects.hash(this.x1, this.y1, this.x2, this.y2, this.type);
        }

        @Override
        public boolean equals(Object o){
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Rectangle rect = (Rectangle) o;
            return this.x1 == rect.x1 && this.x2 == rect.x2 && 
                   this.y1 == rect.y1 && this.y2 == rect.y2 &&
                   this.type.equals(rect.type);
        }
    }
    
    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        mos = new MultipleOutputs<>(context);
    }

    @Override
    protected void reduce(Text key, Iterable<Text> values, Context context)
            throws IOException, InterruptedException {
        
        List<Rectangle> pRects = new ArrayList<>();
        List<Rectangle> qRects = new ArrayList<>();
        List<Rectangle> rRects = new ArrayList<>();
        List<Rectangle> sRects = new ArrayList<>();

        Configuration conf = context.getConfiguration();
        cell_height = conf.getInt("c_height", 100);
        cell_width = conf.getInt("c_width", 100); 
        k = conf.getInt("cell_dim", 3);
        
        for (Text value : values) {
            String[] parts = value.toString().split("\\|");
            if (parts.length < 5) continue;
            
            Rectangle rect = new Rectangle(
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                parts[4]
            );
            
            if ("P".equals(rect.type)) pRects.add(rect);
            else if ("Q".equals(rect.type)) qRects.add(rect);
            else if ("R".equals(rect.type)) rRects.add(rect);
            else if ("S".equals(rect.type)) sRects.add(rect);
        }
        
        Marked = new HashSet<>();

        do_marking(pRects, qRects, rRects, sRects, key.toString(), context);

        for (Rectangle rect : Marked){
            if (project(rect, key.toString())) {          
                context.write(NullWritable.get(), new Text(rect.toInputString()));
            }
        }
    }

    private int getCellIdForPoint(int x, int y){
        int c_col = x / cell_width;
        int c_row = y / cell_height;
        return c_row * k + c_col;
    }

    private boolean crossesCellBoundary(Rectangle rect){
        int bottomLeftCell = getCellIdForPoint(rect.x1, rect.y1);
        int topRightCell = getCellIdForPoint(rect.x2, rect.y2);
        
        if (bottomLeftCell != topRightCell) {
            return true;
        }

        int topLeftCell = getCellIdForPoint(rect.x1, rect.y2);
        int bottomRightCell = getCellIdForPoint(rect.x2, rect.y1);

        return !(bottomLeftCell == topLeftCell && bottomLeftCell == bottomRightCell);
    }

    private boolean overlaps(Rectangle r1, Rectangle r2) {
        return !(r1.x2 < r2.x1 || r2.x2 < r1.x1 || r1.y2 < r2.y1 || r2.y2 < r1.y1);
    }    

    private boolean project(Rectangle r, String currentCellId) {
        int cellId = Integer.parseInt(currentCellId);
        int homeCellId = getCellIdForPoint(r.x1, r.y1);
        return cellId == homeCellId;
    }
    
    private void do_marking(List<Rectangle> pRects, List<Rectangle> qRects, List<Rectangle> rRects, List<Rectangle> sRects, String currentCellId, Context context) throws IOException, InterruptedException {

        for (Rectangle q : qRects){
            for (Rectangle r : rRects){
                if (overlaps(q, r)){
                    
                    if(!crossesCellBoundary(q) && !crossesCellBoundary(r)){
                        for (Rectangle p : pRects){
                            if (overlaps(p, q)){
                               for (Rectangle s : sRects){
                                if (overlaps(r, s)) {
                                    String result = String.format("P%s ⋈ Q%s ⋈ R%s ⋈ S%s",
                                        p.toString(), q.toString(), r.toString(), s.toString());
                                    
                                    mos.write("finalJoins", new Text("JOIN_RESULT"), new Text(result));
                                }
                               } 
                            }
                        }
                    }
                    else {
                        for (Rectangle p : pRects){
                            if (overlaps(p, q)) Marked.add(p);
                        }
                        for (Rectangle s : sRects){
                            if (overlaps(r, s)) Marked.add(s);
                        }
                        Marked.add(q);
                        Marked.add(r);
                    }
                }
            }
        }

        for(Rectangle q : qRects){
            if (crossesCellBoundary(q)){
                for (Rectangle p : pRects){
                    if (overlaps(p, q)) Marked.add(p);
                }
            }
        }
        
        for(Rectangle r : rRects){
            if (crossesCellBoundary(r)){
                for (Rectangle s : sRects){
                    if (overlaps(s, r)) Marked.add(s);
                }
            }
        }

        for (Rectangle p : pRects) { if (crossesCellBoundary(p)) Marked.add(p); }
        for (Rectangle s : sRects) { if (crossesCellBoundary(s)) Marked.add(s); }
    }

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException {
        mos.close();
    }
}