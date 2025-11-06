import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import scala.Tuple2;

import java.util.*;
import java.util.stream.Collectors;

public class SetSimilarityJoin {

    public static void main(String[] args) {
        SparkSession spark = SparkSession.builder()
                .appName("SetSimilarityJoin")
                .master("local[*]")
                .getOrCreate();

        JavaSparkContext sc = new JavaSparkContext(spark.sparkContext());

        // ---------------- Input Data ----------------
        List<Tuple2<String, List<String>>> data = Arrays.asList(
                new Tuple2<>("r1", Arrays.asList("apple", "banana", "mango")),
                new Tuple2<>("r2", Arrays.asList("apple", "grape")),
                new Tuple2<>("r3", Arrays.asList("banana", "mango", "grape"))
        );

        JavaRDD<Tuple2<String, List<String>>> rdd = sc.parallelize(data);

        double threshold = 0.5; // Jaccard similarity threshold

        // ---------------- Helper Functions ----------------
        // Sort tokens and compute prefix
        JavaPairRDD<String, Tuple2<String, List<String>>> tokenPairs = rdd.flatMapToPair(record -> {
            String id = record._1;
            List<String> tokens = new ArrayList<>(record._2);
            Collections.sort(tokens);

            int n = tokens.size();
            int prefixLength = n - (int) Math.floor(n * threshold) + 1;
            prefixLength = Math.min(prefixLength, n);

            List<Tuple2<String, Tuple2<String, List<String>>>> results = new ArrayList<>();
            for (int i = 0; i < prefixLength; i++) {
                results.add(new Tuple2<>(tokens.get(i), new Tuple2<>(id, tokens)));
            }
            return results.iterator();
        });

        // Group by token (inverted index)
        JavaPairRDD<String, Iterable<Tuple2<String, List<String>>>> groups = tokenPairs.groupByKey();

        // Generate candidate pairs (combinations)
        JavaPairRDD<Tuple2<String, String>, Tuple2<List<String>, List<String>>> candidates = groups.flatMapToPair(group -> {
            List<Tuple2<String, List<String>>> records = new ArrayList<>();
            group._2.forEach(records::add);

            List<Tuple2<Tuple2<String, String>, Tuple2<List<String>, List<String>>>> pairs = new ArrayList<>();
            for (int i = 0; i < records.size(); i++) {
                for (int j = i + 1; j < records.size(); j++) {
                    Tuple2<String, List<String>> a = records.get(i);
                    Tuple2<String, List<String>> b = records.get(j);
                    pairs.add(new Tuple2<>(new Tuple2<>(a._1, b._1), new Tuple2<>(a._2, b._2)));
                }
            }
            return pairs.iterator();
        });

        // Compute Jaccard similarity
        JavaPairRDD<Tuple2<String, String>, Double> similarPairs = candidates.mapValues(pair -> {
            Set<String> setA = new HashSet<>(pair._1);
            Set<String> setB = new HashSet<>(pair._2);
            Set<String> intersection = new HashSet<>(setA);
            intersection.retainAll(setB);

            Set<String> union = new HashSet<>(setA);
            union.addAll(setB);

            return (double) intersection.size() / union.size();
        }).filter(x -> x._2 >= threshold);

        // ---------------- Output ----------------
        List<Tuple2<Tuple2<String, String>, Double>> results = similarPairs.collect();
        for (Tuple2<Tuple2<String, String>, Double> res : results) {
            System.out.printf("%s - %s : %.2f%n", res._1._1, res._1._2, res._2);
        }

        spark.stop();
    }
}
