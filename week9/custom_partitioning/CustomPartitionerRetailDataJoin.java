import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.Partitioner;
import scala.Tuple2;

public class CustomPartitionerRetailDataJoin {

    // ------------------------------
    // Custom Key Partitioner
    // ------------------------------
    static class KeyPartitioner extends Partitioner {
        private final int numParts;

        public KeyPartitioner(int numParts) {
            this.numParts = numParts;
        }

        @Override
        public int numPartitions() {
            return numParts;
        }

        @Override
        public int getPartition(Object key) {
            return Math.abs(key.hashCode()) % numParts;
        }
    }

    // ------------------------------
    // Main Program
    // ------------------------------
    public static void main(String[] args) {
        // 1. Setup Spark
        SparkSession spark = SparkSession.builder()
                .appName("CustomPartitionerRetailDataJoin")
                .master("local[*]")
                .getOrCreate();

        JavaSparkContext sc = new JavaSparkContext(spark.sparkContext());

        // Define partitioners
        Partitioner custPartitioner = new KeyPartitioner(4);
        Partitioner prodPartitioner = new KeyPartitioner(4);

        // ------------------------------
        // Load Customers
        // ------------------------------
        JavaPairRDD<String, Tuple2<String, String>> customersRDD = sc.textFile("customers.csv")
                .filter(line -> line != null && !line.trim().isEmpty() && !line.startsWith("cust_id"))
                .mapToPair(line -> {
                    String[] parts = line.split(",");
                    if (parts.length < 3) return new Tuple2<>("INVALID", new Tuple2<>("NA", "NA"));
                    return new Tuple2<>(parts[0].trim(),
                            new Tuple2<>(parts[1].trim(), parts[2].trim()));
                })
                .filter(t -> !t._1.equals("INVALID"))
                .partitionBy(custPartitioner);

        // ------------------------------
        // Load Orders
        // ------------------------------
        JavaPairRDD<String, Tuple2<String, Integer>> ordersRDD = sc.textFile("orders.csv")
                .filter(line -> line != null && !line.trim().isEmpty() && !line.startsWith("order_id"))
                .mapToPair(line -> {
                    String[] parts = line.split(",");
                    if (parts.length < 4) return new Tuple2<>("INVALID", new Tuple2<>("NA", 0));
                    return new Tuple2<>(parts[1].trim(),
                            new Tuple2<>(parts[2].trim(), Integer.parseInt(parts[3].trim())));
                })
                .filter(t -> !t._1.equals("INVALID"))
                .partitionBy(custPartitioner);

        // ------------------------------
        // First Join: Customers x Orders
        // ------------------------------
        JavaPairRDD<String, Tuple2<Tuple2<String, String>, Tuple2<String, Integer>>> joinedCustOrders =
                customersRDD.join(ordersRDD);

        // ------------------------------
        // Prepare for second join: re-key on prod_id
        // ------------------------------
        JavaPairRDD<String, Tuple2<String, Tuple2<String, Integer>>> readyForProdJoin =
                joinedCustOrders.mapToPair(entry -> {
                    String name = entry._2._1._1;
                    String city = entry._2._1._2;
                    String prod_id = entry._2._2._1;
                    Integer quantity = entry._2._2._2;

                    return new Tuple2<>(prod_id, new Tuple2<>(name, new Tuple2<>(city, quantity)));
                }).partitionBy(prodPartitioner);

        // ------------------------------
        // Load Products
        // ------------------------------
        JavaPairRDD<String, Tuple2<String, Integer>> productsRDD = sc.textFile("products.csv")
                .filter(line -> line != null && !line.trim().isEmpty() && !line.startsWith("prod_id"))
                .mapToPair(line -> {
                    String[] parts = line.split(",");
                    if (parts.length < 3) return new Tuple2<>("INVALID", new Tuple2<>("NA", 0));
                    return new Tuple2<>(parts[0].trim(),
                            new Tuple2<>(parts[1].trim(), Integer.parseInt(parts[2].trim())));
                })
                .filter(t -> !t._1.equals("INVALID"))
                .partitionBy(prodPartitioner);

        // ------------------------------
        // Final Join: (Cust+Order) x Products
        // ------------------------------
        JavaPairRDD<String, Tuple2<Tuple2<String, Tuple2<String, Integer>>, Tuple2<String, Integer>>> finalJoin =
                readyForProdJoin.join(productsRDD);

        // ------------------------------
        // Compute Final Report
        // ------------------------------
        JavaRDD<String> finalReport = finalJoin.map(entry -> {
            String name = entry._2._1._1;
            String city = entry._2._1._2._1;
            Integer quantity = entry._2._1._2._2;
            String prod_name = entry._2._2._1;
            Integer price = entry._2._2._2;
            Integer total_cost = quantity * price;

            return String.format("Customer=%s, City=%s, Product=%s, Quantity=%d, TotalCost=%d",
                    name, city, prod_name, quantity, total_cost);
        });

        System.out.println("--- Final Retail Report (Custom Partitioner) ---");
        finalReport.collect().forEach(System.out::println);

        spark.stop();
    }
}

