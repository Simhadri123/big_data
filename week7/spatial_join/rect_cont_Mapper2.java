package week6.rect_cont_join;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.conf.Configuration;
import java.io.IOException;
import java.util.*;

// PHASE 1 MAPPER
public class rect_cont_Mapper2 extends Mapper<LongWritable, Text, Text, Text> {        
    
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

    protected int[] get_current_cell(int x1, int y1, int x2, int y2){
        // get the cell this rectangle belongs to
        // get the bottom left corner and get cell corresponding to that
        int c_row = x1 / cell_width;
        int c_col = y1 / cell_height;

        return new int[]{c_row, c_col};
    }

    protected List<Integer> get_cells(int c_row, int c_col){
        // return all cells to the right of c and above c
        List<Integer> cells = new ArrayList<>();
        for(int i = 0; i < k; i++){
            for (int j = 0; j < k; j++){
                if (i >= c_row && j >= c_col){
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

        int[] curr_cell = get_current_cell(x1, y1, x2, y2);

        List<Integer> rep_cells = get_cells(curr_cell[0], curr_cell[1]);                

        // Emit (c, p) - for each c in rep_cells, for a cell collect all rects in it
        for (int c : rep_cells){
            context.write(new Text(String.valueOf(c)), 
                         new Text(String.format("%d|%d|%d|%d|%s", x1, y1, x2, y2, rect_type)));    
        }           
    }
}