package week6.rect_cont_join;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import java.io.IOException;
import java.util.*;

public class rect_cont_Reducer2 extends Reducer<Text, Text, Text, Text> {
    
    private int k; // Grid dimension (k x k reducers)
    private int cell_width;
    private int cell_height;
    
    private static class Rectangle {
        int x1, y1, x2, y2;
        String type;
        
        Rectangle(int x1, int y1, int x2, int y2, String type) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.type = type;
        }
        
        @Override
        public String toString() {
            return String.format("(%d,%d,%d,%d)", x1, y1, x2, y2);
        }
    }
    
    @Override
    protected void reduce(Text key, Iterable<Text> values, Context context)
            throws IOException, InterruptedException {
        
        List<Rectangle> pRects = new ArrayList<>();
        List<Rectangle> qRects = new ArrayList<>();
        List<Rectangle> rRects = new ArrayList<>();
        List<Rectangle> sRects = new ArrayList<>();

        cell_height = context.getConfiguration().getInt("c_height", 100);
        cell_width = context.getConfiguration().getInt("c_width", 100); 
        k = context.getConfiguration().getInt("cell_dim", 3);
        
        for (Text value : values) {
            String[] parts = value.toString().split("\\|");
            if (parts.length < 5) continue;
            
            int x1 = Integer.parseInt(parts[0]);
            int y1 = Integer.parseInt(parts[1]);
            int x2 = Integer.parseInt(parts[2]);
            int y2 = Integer.parseInt(parts[3]);
            String type = parts[4];
            
            Rectangle rect = new Rectangle(x1, y1, x2, y2, type);
            
            if ("P".equals(type)) {
                pRects.add(rect);
            } else if ("Q".equals(type)) {
                qRects.add(rect);
            } else if ("R".equals(type)) {
                rRects.add(rect);
            } else if ("S".equals(type)) {
                sRects.add(rect);
            }
        }
        
        performSpatialJoin(pRects, qRects, rRects, sRects, key.toString(), context);
    }
    
    // Check if two rectangles overlap
    private boolean overlaps(Rectangle r1, Rectangle r2) {
        return !(r1.x2 < r2.x1 || r2.x2 < r1.x1 || 
                 r1.y2 < r2.y1 || r2.y2 < r1.y1);
    }
    
    private boolean canComputeOutput(Rectangle p, String cellId) {
        int c = Integer.parseInt(cellId);
        
        //get the bottom left corner of rect and check if it is in this cell

        // get the cell width and hiefht       

        int c_row = p.x1 / cell_width;
        int c_col = p.y1 / cell_height;

        if (c_row*k + c_col == c){
            // this cell contains this rects bottom left then join
            return true;
        }
        return false;

    }
    
    private void performSpatialJoin(List<Rectangle> pRects,
                                   List<Rectangle> qRects,
                                   List<Rectangle> rRects,
                                   List<Rectangle> sRects,
                                   String cellId,
                                   Context context) throws IOException, InterruptedException {
        
        for (Rectangle p : pRects) {
            for (Rectangle q : qRects) {
                if (!overlaps(p, q)) continue;
                
                for (Rectangle r : rRects) {
                    if (!overlaps(q, r)) continue;
                    
                    for (Rectangle s : sRects) {
                        if (!overlaps(r, s)) continue;
                        
                        // Check duplicate avoidance
                        if (canComputeOutput(p, cellId)) {
                            String result = String.format("P%s ⋈ Q%s ⋈ R%s ⋈ S%s",
                                p.toString(), q.toString(), r.toString(), s.toString());
                            context.write(new Text("JOIN_RESULT"), new Text(result));
                        }
                    }
                }
            }
        }
    }
}