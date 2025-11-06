package week6.rect_cont_join;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.conf.Configuration;
import java.io.IOException;
import java.util.*;

// PHASE 1 MAPPER
public class rect_cont_Mapper1 extends Mapper<LongWritable, Text, Text, Text> {        
    
    private int k; // (k x k reducers)
    private int cell_width;
    private int cell_height;

    
    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        Configuration conf = context.getConfiguration();
        k = conf.getInt("cell_dim", 3);
        cell_height = conf.getInt("c_height", 100);
        cell_width = conf.getInt("c_width", 100);        
    }

    private boolean overlaps(int x11, int x12, int y11, int y12, int x21, int x22, int y21, int y22) {     
        return !(x12 < x21 || x22 < x11 || 
                 y12 < y21 || y22 < y11);
    
    }

    private List<Integer> get_cells(int x1, int y1, int x2, int y2) {
        
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < k; i++) { // Row
            for (int j = 0; j < k; j++) { // Column
                // Cell boundaries
                int cell_x1 = j * cell_width;
                int cell_x2 = (j + 1) * cell_width;
                int cell_y1 = i * cell_height;
                int cell_y2 = (i + 1) * cell_height;

                if (overlaps(x1, x2, y1, y2, cell_x1, cell_x2, cell_y1, cell_y2)) {
                    cells.add(i * k + j);
                }
            }
        }
        return cells;
    }

    @Override
    protected void map(LongWritable key, Text value, Context context) 
            throws IOException, InterruptedException {
        
        String line = value.toString().trim();
        if (line.isEmpty()) return;
        
        String[] parts = line.split("\\s+");
        if (parts.length < 5) return;

        // Input format: "x1 y1 x2 y2 rect_type"
        int x1 = Integer.parseInt(parts[0]);
        int y1 = Integer.parseInt(parts[1]);
        int x2 = Integer.parseInt(parts[2]);
        int y2 = Integer.parseInt(parts[3]);
        String rect_type = parts[4]; 

        // split operation - get all the cells rect overlaps 
        List<Integer> overlap_cells = get_cells(x1, y1, x2, y2);

        // Emit (c, p) - for each c in rep_cells, for a cell collect all rects in it
        for (int c : overlap_cells){
            context.write(new Text(String.valueOf(c)), 
                         new Text(String.format("%d|%d|%d|%d|%s", x1, y1, x2, y2, rect_type)));    
        }           
    }
}