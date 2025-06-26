package com.example;

import static org.apache.spark.sql.functions.*;
import org.apache.spark.sql.*;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;

import java.util.Arrays;

public class App {
    public static void main(String[] args) {
        SparkSession spark = null;
        try {
            spark = SparkSession.builder()
                .appName("Speed Zones Calculator")
                .getOrCreate();

            // * = Let Spark decide how to read all CSV files
            String momentsPath =  "hdfs:///data/nba_movement_data/moments/*.csv"; // Read all CSV files

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
                    col("game_id").cast("int"),
                    col("x_loc").cast("float"),
                    col("y_loc").cast("float"),
                    col("game_clock").cast("float"),
                    col("shot_clock").cast("float")
                )
                .withColumn("original_order", monotonically_increasing_id())
                // Convert feet (Freedom units per BBQ squared) to meters 
                // (objectively more meaningful measurement)
                .withColumn("x_loc", col("x_loc").multiply(0.3048))
                .withColumn("y_loc", col("y_loc").multiply(0.3048))
                //remove duplicates (different events have same timestamps
                // and coords that are messing with the moving average (lots of rows having 0 diff))
                .dropDuplicates("player_id", "quarter", "game_id", "x_loc", "y_loc", "game_clock", "shot_clock")
                .cache();

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

            // Calculate speed at every moment
            // (essentialy eucl distance code with the dt factor multiplied)
            Dataset<Row> speedData = moments
                .withColumn("prev_x", lag(col("x_loc"), 1).over(windowSpec))
                .withColumn("prev_y", lag(col("y_loc"), 1).over(windowSpec))
                .withColumn("euclidean_dist",
                    when(col("prev_x").isNull().or(col("prev_y").isNull()), lit(0))
                    .otherwise(sqrt(pow(col("x_loc").minus(col("prev_x")), 2)
                                .plus(pow(col("y_loc").minus(col("prev_y")), 2)))))
                .withColumn("speed", col("euclidean_dist").multiply(25))
                .withColumn("speed_valid",
                    when(col("speed").leq(12), col("speed")).otherwise(lit(null)));

            // Define a moving average window: partition by player, quarter and game, ordered by game_clock.
            WindowSpec movingAvgWindow = Window.partitionBy("player_id", "quarter", "game_id", "time_id")
                .orderBy("original_order")
                .rowsBetween(-9, 0); 

            // Filter speeds and compute moving average
            // If speed over 12 --> NULL (the moving averaging filter will fix it)
            Dataset<Row> zoneData = speedData
            .withColumn("moving_avg", avg("speed_valid").over(movingAvgWindow))
            .withColumn("zone",
                when(col("moving_avg").lt(2), "slow")
                .when(col("moving_avg").gt(7), "fast")
                .otherwise("normal"));

            // Compute euclidean distance and zone changes
            Dataset<Row> distanceData = zoneData
                .withColumn("prev_zone", lag(col("zone"), 1).over(windowSpec))
                .withColumn("zone_change", 
                    when(col("prev_zone").isNull(), lit(0))
                    .otherwise(when(col("zone").notEqual(col("prev_zone")), lit(1))
                                .otherwise(lit(0))))
                .withColumn("run_id", sum("zone_change").over(windowSpec));

            // Generating a CSV output that ensures all zones are represented, 
            // even those a player never reached.  
            // A simple groupBy would exclude zones with no player activity.
            // Create a DataFrame of all unique players.
            Dataset<Row> allPlayersDF = distanceData.select("player_id").distinct();

            // Create a DataFrame for all possible speed zones.
            Dataset<Row> allZonesDF = spark.createDataset(
                Arrays.asList("slow", "normal", "fast"),
                Encoders.STRING()
            ).toDF("zone");

            // Create all possible combinations of players and zones.
            Dataset<Row> allCombinationsDF = allPlayersDF.crossJoin(allZonesDF);

            // Group by player and zone, aggregating the counts and sums.
            Dataset<Row> runsSummary = distanceData.groupBy("player_id", "zone")
                .agg(
                    countDistinct("run_id").alias("number_of_runs"),
                    round(sum("euclidean_dist"), 2).alias("total_distance_traveled")
                );

            // Left join the all combinations DataFrame with the aggregated results.
            Dataset<Row> joined = allCombinationsDF.join(runsSummary, new String[]{"player_id", "zone"}, "left_outer");

            // Fill null values (which occur when a player doesn't have data for a zone) with zeros.
            Dataset<Row> completeResults = joined.na().fill(0)
                .orderBy("player_id", "zone");
            
            // Write output
            completeResults.coalesce(1)
                .write()
                .mode("overwrite")
                .option("header", false)
                .option("delimiter", " ")
                .csv("Speedzones");

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