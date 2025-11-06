import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import scala.Tuple2;

public class RetailDataJoinDefault {
    public static void main(String[] args) {
        SparkSession spark = SparkSession.builder()
            .appName("RetailDataJoinDefault")
            .master("local[*]")
            .getOrCreate();

        JavaSparkContext sc = new JavaSparkContext(spark.sparkContext());

        // --- CUSTOMERS ---
        JavaPairRDD<String, Tuple2<String, String>> customersRDD = sc.textFile("customers.csv")
            .filter(line -> line != null && !line.trim().isEmpty() && !line.startsWith("cust_id"))
            .mapToPair(line -> {
                String[] parts = line.split(",");
                if (parts.length < 3) return new Tuple2<>("INVALID", new Tuple2<>("NA", "NA"));
                return new Tuple2<>(parts[0].trim(), new Tuple2<>(parts[1].trim(), parts[2].trim()));
            })
            .filter(t -> !t._1.equals("INVALID"));

        // --- ORDERS ---
        JavaPairRDD<String, Tuple2<String, Integer>> ordersRDD = sc.textFile("orders.csv")
            .filter(line -> line != null && !line.trim().isEmpty() && !line.startsWith("order_id"))
            .mapToPair(line -> {
                String[] parts = line.split(",");
                if (parts.length < 4) return new Tuple2<>("INVALID", new Tuple2<>("NA", 0));
                return new Tuple2<>(parts[1].trim(), new Tuple2<>(parts[2].trim(), Integer.parseInt(parts[3].trim())));
            })
            .filter(t -> !t._1.equals("INVALID"));

        // --- JOIN customers x orders ---
        JavaPairRDD<String, Tuple2<Tuple2<String, String>, Tuple2<String, Integer>>> joinedCustOrders =
                customersRDD.join(ordersRDD);

        // --- Prepare for next join on prod_id ---
        JavaPairRDD<String, Tuple2<String, Tuple2<String, Integer>>> readyForProdJoin =
                joinedCustOrders.mapToPair(entry -> {
                    String name = entry._2._1._1;
                    String city = entry._2._1._2;
                    String prod_id = entry._2._2._1;
                    Integer quantity = entry._2._2._2;
                    return new Tuple2<>(prod_id, new Tuple2<>(name, new Tuple2<>(city, quantity)));
                });

        // --- PRODUCTS ---
        JavaPairRDD<String, Tuple2<String, Integer>> productsRDD = sc.textFile("products.csv")
            .filter(line -> line != null && !line.trim().isEmpty() && !line.startsWith("prod_id"))
            .mapToPair(line -> {
                String[] parts = line.split(",");
                if (parts.length < 3) return new Tuple2<>("INVALID", new Tuple2<>("NA", 0));
                return new Tuple2<>(parts[0].trim(), new Tuple2<>(parts[1].trim(), Integer.parseInt(parts[2].trim())));
            })
            .filter(t -> !t._1.equals("INVALID"));

        // --- FINAL JOIN ---
        JavaPairRDD<String, Tuple2<Tuple2<String, Tuple2<String, Integer>>, Tuple2<String, Integer>>> finalJoin =
                readyForProdJoin.join(productsRDD);

        // --- OUTPUT ---
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

        System.out.println("--- Final Retail Report (Default Join) ---");
        finalReport.collect().forEach(System.out::println);

        spark.stop();
    }
}

