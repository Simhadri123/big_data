import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.Partitioner;
import scala.Tuple2;
import java.util.*;
import java.util.stream.Collectors;

public class SharesSkewRetailJoin {

    // ------------------------------
    // Custom Key Partitioner (for SharesSkew)
    // ------------------------------
    static class KeyPartitioner extends Partitioner {
        private final int numParts;
        public KeyPartitioner(int numParts) { this.numParts = numParts; }
        @Override public int numPartitions() { return numParts; }
        @Override public int getPartition(Object key) {
            return Math.abs(key.hashCode()) % numParts;
        }
    }

    public static void main(String[] args) {
        SparkSession spark = SparkSession.builder()
                .appName("SharesSkewRetailJoin")
                .master("local[*]")
                .getOrCreate();
        JavaSparkContext sc = new JavaSparkContext(spark.sparkContext());

        // Define reducers (shares)
        int numReducers = 8;
        Partitioner skewPartitioner = new KeyPartitioner(numReducers);

        // ------------------------------
        // Load Customers
        // ------------------------------
        JavaPairRDD<String, Tuple2<String, String>> customersRDD = sc.textFile("skewed_customers.csv")
                .filter(line -> !line.startsWith("cust_id") && !line.trim().isEmpty())
                .mapToPair(line -> {
                    String[] p = line.split(",");
                    return new Tuple2<>(p[0].trim(), new Tuple2<>(p[1].trim(), p[2].trim()));
                })
                .partitionBy(skewPartitioner);

        // ------------------------------
        // Load Orders
        // ------------------------------
        JavaPairRDD<String, Tuple2<String, Integer>> ordersRDD = sc.textFile("skewed_orders.csv")
                .filter(line -> !line.startsWith("order_id") && !line.trim().isEmpty())
                .mapToPair(line -> {
                    String[] p = line.split(",");
                    return new Tuple2<>(p[1].trim(), new Tuple2<>(p[2].trim(), Integer.parseInt(p[3].trim())));
                });

        // ------------------------------
        // Detect Skewed Keys (heavy customers)
        // ------------------------------
        Map<String, Long> keyCounts = ordersRDD.countByKey();
        long threshold = 5; // simple threshold for heavy hitter detection
        Set<String> heavyKeys = keyCounts.entrySet().stream()
                .filter(e -> e.getValue() > threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // ------------------------------
        // Split heavy and normal orders
        // ------------------------------
        JavaPairRDD<String, Tuple2<String, Integer>> heavyOrders = ordersRDD.filter(t -> heavyKeys.contains(t._1));
        JavaPairRDD<String, Tuple2<String, Integer>> normalOrders = ordersRDD.filter(t -> !heavyKeys.contains(t._1));

        // ------------------------------
        // Handle heavy keys (SharesSkew logic)
        // - Each heavy key gets multiple "shares" (reducers)
        // ------------------------------
        int sharesPerHeavyKey = 4; // split heavy key across 4 reducers

        // Expand heavy keys with random salt (simulate multiple reducers)
        JavaPairRDD<Tuple2<String, Integer>, Tuple2<String, Integer>> saltedHeavyOrders =
                heavyOrders.flatMapToPair(t -> {
                    List<Tuple2<Tuple2<String, Integer>, Tuple2<String, Integer>>> salted = new ArrayList<>();
                    for (int i = 0; i < sharesPerHeavyKey; i++) {
                        salted.add(new Tuple2<>(new Tuple2<>(t._1, i), t._2));
                    }
                    return salted.iterator();
                });

        // Replicate customer records for heavy keys to all shares
        JavaPairRDD<Tuple2<String, Integer>, Tuple2<String, String>> saltedHeavyCustomers =
                customersRDD.filter(t -> heavyKeys.contains(t._1))
                        .flatMapToPair(t -> {
                            List<Tuple2<Tuple2<String, Integer>, Tuple2<String, String>>> replicated = new ArrayList<>();
                            for (int i = 0; i < sharesPerHeavyKey; i++) {
                                replicated.add(new Tuple2<>(new Tuple2<>(t._1, i), t._2));
                            }
                            return replicated.iterator();
                        });

        // Normal join for non-heavy keys
        JavaPairRDD<String, Tuple2<Tuple2<String, String>, Tuple2<String, Integer>>> normalJoin =
                customersRDD.join(normalOrders).partitionBy(skewPartitioner);

        // Join heavy salted data
        JavaPairRDD<Tuple2<String, Integer>, Tuple2<Tuple2<String, String>, Tuple2<String, Integer>>> heavyJoin =
                saltedHeavyCustomers.join(saltedHeavyOrders);

        // Merge heavy + normal join results (normalize key)
        JavaPairRDD<String, Tuple2<Tuple2<String, String>, Tuple2<String, Integer>>> unifiedJoin =
                heavyJoin.mapToPair(e -> new Tuple2<>(e._1._1, e._2)).union(normalJoin);

        // ------------------------------
        // Prepare for product join (SharesSkew on prod_id)
        // ------------------------------
        JavaPairRDD<String, Tuple2<String, Tuple2<String, Integer>>> readyForProdJoin =
                unifiedJoin.mapToPair(entry -> {
                    String name = entry._2._1._1;
                    String city = entry._2._1._2;
                    String prod_id = entry._2._2._1;
                    Integer qty = entry._2._2._2;
                    return new Tuple2<>(prod_id, new Tuple2<>(name, new Tuple2<>(city, qty)));
                }).partitionBy(skewPartitioner);

        // ------------------------------
        // Load Products
        // ------------------------------
        JavaPairRDD<String, Tuple2<String, Integer>> productsRDD = sc.textFile("skewed_products.csv")
                .filter(line -> !line.startsWith("prod_id") && !line.trim().isEmpty())
                .mapToPair(line -> {
                    String[] p = line.split(",");
                    return new Tuple2<>(p[0].trim(), new Tuple2<>(p[1].trim(), Integer.parseInt(p[2].trim())));
                }).partitionBy(skewPartitioner);

        // ------------------------------
        // Final Join: (Customers+Orders) × Products
        // ------------------------------
        JavaPairRDD<String, Tuple2<Tuple2<String, Tuple2<String, Integer>>, Tuple2<String, Integer>>> finalJoin =
                readyForProdJoin.join(productsRDD);

        // ------------------------------
        // Compute final report
        // ------------------------------
        JavaRDD<String> report = finalJoin.map(e -> {
            String name = e._2._1._1;
            String city = e._2._1._2._1;
            int qty = e._2._1._2._2;
            String prodName = e._2._2._1;
            int price = e._2._2._2;
            int total = qty * price;
            return String.format("Customer=%s, City=%s, Product=%s, Quantity=%d, TotalCost=%d",
                    name, city, prodName, qty, total);
        });

        System.out.println("--- Final Retail Report (SharesSkew Join) ---");
        report.collect().forEach(System.out::println);

        spark.stop();
    }
}

