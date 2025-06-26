package com.example;

import static org.apache.spark.sql.functions.*;
import org.apache.spark.sql.*;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;

public class App {
    public static void main(String[] args) {
        SparkSession spark = null;
        try {
            spark = SparkSession.builder()
                .appName("Distance Calculator")
                .config("spark.master", "local")
                .getOrCreate();
                
            String minutesPlayedPath = "hdfs:///data/nba_movement_data/minutes_played.csv";
            String momentsPath = "hdfs:///data/nba_movement_data/moments/*.csv"; // Read all CSV files

            // Load minutes played data
            //Remove players appearing to the list but have no play time
            Dataset<Row> minPlayed = spark.read()
                .option("header", true)
                .csv(minutesPlayedPath)
                .filter(col("SEC").isNotNull())
                .withColumn("SEC", col("SEC").cast("float"))
                .withColumn("PLAYER_ID", col("PLAYER_ID").cast("int"))
                .groupBy("PLAYER_ID")
                    .agg(sum("SEC").alias("total_seconds"))
                    .withColumn("total_minutes", col("total_seconds").divide(lit(60)))
                .select("PLAYER_ID", "total_minutes")
                .cache(); // Cache this since it's small and used later

            // Load all moments data at once
            Dataset<Row> moments = spark.read()
                .option("header", true)
                .csv(momentsPath)
                // Drop the ball (Hopefully only from the database)
                .filter("player_id != -1")
                // Cast to correct values
                .select(
                    col("player_id").cast("int"),
                    col("quarter").cast("int"),
                    col("game_id").cast("string"),
                    col("x_loc").cast("float"),
                    col("y_loc").cast("float"),
                    col("game_clock").cast("float"),
                    col("shot_clock").cast("float")
                )
                .withColumn("original_order", monotonically_increasing_id())
                // Convert feet (Freedom units per BBQ squared) to meters 
                // (objectively more meaningful measurement)
                .withColumn("x_loc", col("x_loc").multiply(0.3048))
                .withColumn("y_loc", col("y_loc").multiply(0.3048));

            // remove duplicates (different events have same timestamps and 
            // coords and they are messing with the averages (lots of rows having 0 diff))
            moments = moments
                .dropDuplicates("player_id", "quarter", "game_id",
                                "x_loc", "y_loc", "game_clock", "shot_clock");

            // Get a window that is divided by player_id, quarter
            // (prevent teleportation between quarters) but also game
            // (prevent teleportation between games)
            WindowSpec windowSpec = Window
                .partitionBy("player_id", "quarter", "game_id")
                .orderBy("original_order");

            moments = moments
            //calculate time diffs (will be used to find time jumps i.e. substitutions)
            .withColumn("prev_game_clock",
                when(lag(col("game_clock"), 1).over(windowSpec).isNull(), col("game_clock"))
                    .otherwise(lag(col("game_clock"), 1).over(windowSpec)))
                // Create a boolean value to signify jump in time (player substituted)
                .withColumn("diff", col("game_clock").minus(col("prev_game_clock")))
                .withColumn("new_time_flag", 
                // A reasonable assumption was made that a player is out for more than 15 seconds
                // The threshold was set because sometimes the game clock is going "back"
                // a few seconds (a maximum of 8.9 was observed)
                    when(abs(col("diff")).gt(15), lit(1)) 
                    .otherwise(lit(0)))
                .withColumn("prev_flag", lag(col("new_time_flag"), 1).over(windowSpec))
                .withColumn("flag_change", when(col("new_time_flag").equalTo(1)
                    .and(col("prev_flag").equalTo(0)), lit(1)).otherwise(lit(0)));

            //window to determine time jumps
            WindowSpec cumWindow = Window
                .partitionBy("player_id", "quarter", "game_id")
                .orderBy("original_order")
                .rowsBetween(Window.unboundedPreceding(), Window.currentRow());

            moments = moments
                .withColumn("time_id", sum("flag_change").over(cumWindow))
                .drop("prev_game_clock", "new_time_flag", "diff", "prev_flag", "flag_change");
            
            //partition by player and also:
            // a) by quarter, since a quarter breaks a speed zone
            // b) per game, for obvious reasons
            // c) time_id, to partition jumps in time that signify player substitution
            windowSpec = Window
                .partitionBy("player_id", "quarter", "game_id", "time_id")
                .orderBy("original_order");

            // Calculate Euclidean distance in one pass
            Dataset<Row> euclDist = moments
                .withColumn("prev_x", lag(col("x_loc"), 1).over(windowSpec))
                .withColumn("prev_y", lag(col("y_loc"), 1).over(windowSpec))
                .withColumn("distance", 
                    when(col("prev_x").isNull().or(col("prev_y").isNull()), lit(0.0f))
                    .otherwise(
                        sqrt(pow(col("x_loc").minus(col("prev_x")), 2)
                        .plus(pow(col("y_loc").minus(col("prev_y")), 2)))))
                // Use a more targeted select to reduce data shuffling
                .select("player_id", "distance")
                .groupBy("player_id")
                .agg(sum("distance").alias("Total_eucl_dist"))
                .cache(); // Cache this as it's used in the join

            // Join with minutes played data
            Dataset<Row> result = minPlayed.alias("mp")
                .join(
                    euclDist.alias("ed"),
                    col("mp.PLAYER_ID").equalTo(col("ed.player_id")),
                    "left_outer"
                )
                .select(
                    col("mp.PLAYER_ID").alias("player_id"),
                    col("total_minutes"),
                    coalesce(col("ed.Total_eucl_dist"), lit(0)).alias("Total_eucl_dist")
                )
                .withColumn(
                    "Adjusted_Distance",
                    bround(expr("Total_eucl_dist * 12 / total_minutes"), 0)
                )
                .drop("total_minutes", "Total_eucl_dist");

            minPlayed.unpersist();
            euclDist.unpersist();
            // Write output
            result.coalesce(1)
                .write()
                .mode("overwrite")
                .option("header", false)
                .option("delimiter", " ")
                .csv("hdfs:///user/r1017513/distTravelled");

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (spark != null) {
                        spark.stop();
                        System.out.println("Algorithm finished but will linger to bottleneck the server muahahah");
                        System.out.println("JK, Spark stopped successfully");
                        System.out.println("Asking nicely the JVM to exit");
                        System.exit(0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }
    }