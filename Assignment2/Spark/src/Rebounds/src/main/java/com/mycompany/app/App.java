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
                .appName("Rebounds Calculator")
                .getOrCreate();

            String momentsPath =  "hdfs:///data/nba_movement_data/moments/*.csv"; // Read all CSV files
            String eventsPath =  "hdfs:///data/nba_movement_data/events/*.csv"; // Read all CSV files

            // Load all events data at once
            Dataset<Row> events = spark.read()
                .option("header", true)
                .csv(eventsPath)
            
                // Drop unnecessary columns to speed up performance
                .drop("WCTIMESTRING", "NEUTRALDESCRIPTION", "EVENTMSGACTIONTYPE",
                    "SCORE", "SCOREMARGIN", "PERSON1TYPE", "PLAYER1_NAME", "PLAYER1_TEAM_CITY",
                    "PLAYER1_TEAM_ABBREVIATION",  "PLAYER1_TEAM_NICKNAME",
                    "PERSON2TYPE", "PLAYER2_ID", "PLAYER2_NAME", "PLAYER2_TEAM_ID",
                    "PLAYER2_TEAM_CITY", "PLAYER2_TEAM_NICKNAME", "PLAYER2_TEAM_ABBREVIATION",
                    "PERSON3TYPE", "PLAYER3_ID", "PLAYER3_NAME", "PLAYER3_TEAM_ID",
                    "PLAYER3_TEAM_CITY", "PLAYER3_TEAM_NICKNAME", "PLAYER3_TEAM_ABBREVIATION")
            
                // Cast necessary columns to correct types
                .withColumn("EVENTMSGTYPE", col("EVENTMSGTYPE").cast("int"))
                .withColumn("PLAYER1_TEAM_ID", col("PLAYER1_TEAM_ID").cast("int"))
                .withColumn("EVENTNUM", col("EVENTNUM").cast("int"))
                .withColumn("GAME_ID", col("GAME_ID").cast("int"))
                .withColumn("PLAYER1_ID", col("PLAYER1_ID").cast("int"))
            
                // Filter relevant rows. Extract rebounds (=4) and also
                // Filter team rebounds (credited to team gaining possession after a stop in play
                .filter("EVENTMSGTYPE = 4 AND PLAYER1_TEAM_ID IS NOT NULL")
            
                // Convert "PCTIMESTRING" (MM:SS) to game_clock in seconds
                // (To correlate with moments)
                .withColumn("game_clock",
                    expr("cast(cast(split(PCTIMESTRING, ':')[0] as int) * 60 + cast(split(PCTIMESTRING, ':')[1] as int) as float)"))
                
                // Drop columns no longer needed
                .drop("EVENTMSGTYPE", "PCTIMESTRING")

                // Combine home desrciption and visitor description to a single desc
                // (descriptions will be used to extract max off end def rebounds with regex) 
                .withColumn("description", coalesce(col("HOMEDESCRIPTION"), col("VISITORDESCRIPTION")))

                // Drop used columns for speed and clarity
                .drop("HOMEDESCRIPTION", "VISITORDESCRIPTION")

                // Extract offensive and defensive rebounds with pattern matching
                // (ex. Plumlee REBOUND (Off:0 Def:1) --> Off 0, Def 1)
                .withColumn("off_rebound", 
                    regexp_extract(col("description"), ".*\\(Off:(\\d+) Def:(\\d+)\\).*", 1).cast("int"))
                .withColumn("def_rebound", 
                    regexp_extract(col("description"), ".*\\(Off:(\\d+) Def:(\\d+)\\).*", 2).cast("int"));

                //Done with description, drop it.
                //.drop("description");
                
            // For each player and game, take the maximum off_rebound and def_rebound.
            Dataset<Row> reboundsByGame = events.groupBy("PLAYER1_ID", "GAME_ID")
                .agg(
                    max("off_rebound").alias("max_off_rebound"),
                    max("def_rebound").alias("max_def_rebound"),
                    first("PLAYER1_TEAM_ID").alias("team_id")
                );

            // Team rebounds aggregation
            Dataset<Row> teamReboundsByGame = reboundsByGame.groupBy("team_id", "GAME_ID")
                .agg(
                    sum("max_off_rebound").alias("total_off_rebounds_per_game"),
                    sum("max_def_rebound").alias("total_def_rebounds_per_game")
                );

            // Get average rebounds per game for every team
            Dataset<Row> teamAvgRebounds = teamReboundsByGame.groupBy("team_id")
                .agg(
                    avg(col("total_off_rebounds_per_game")
                        .plus(col("total_def_rebounds_per_game")))
                        .alias("avg_total_rebounds_per_game")
                );

            // For each player, sum the maximum rebounds from all games.
            Dataset<Row> playerRebounds = reboundsByGame.groupBy("PLAYER1_ID")
                .agg(
                    sum("max_off_rebound").alias("total_off_rebounds"),
                    sum("max_def_rebound").alias("total_def_rebounds")
                );

            // Load all moments data at once
            Dataset<Row> moments = spark.read()
                .option("header", true)
                .csv(momentsPath)
                // Cast to correct values
                .select(
                    col("player_id").cast("int"),
                    col("quarter").cast("int"),
                    col("game_id").cast("int"),
                    col("x_loc").cast("float"),
                    col("y_loc").cast("float"),
                    col("game_clock").cast("float"),
                    col("shot_clock").cast("float"),
                    col("event_id").cast("int")
                )
                .withColumn("original_order", monotonically_increasing_id())
                // Convert feet (Freedom units per BBQ squared) to meters 
                // (objectively more meaningful measurement)
                .withColumn("x_loc", col("x_loc").multiply(0.3048))
                .withColumn("y_loc", col("y_loc").multiply(0.3048));
                // don't remove duplicates, we will lose rebound events

            // Ball will be used to determine optimal rebound position out of the candidates
            Dataset<Row> ballMoments = moments
                .filter("player_id = -1")
                // Rename for clarity and easiness of join
                .select(
                    col("game_id").alias("ball_game_id"),
                    col("quarter").alias("ball_quarter"),
                    col("event_id").alias("ball_event_id"),
                    col("game_clock").alias("ball_game_clock"),
                    col("shot_clock").alias("ball_shot_clock"),
                    col("x_loc").alias("ball_x"),
                    col("y_loc").alias("ball_y")
                );

            //Ball is on the other dataset, drop it from the big one to save space
            moments = moments.filter("player_id != -1");

            WindowSpec windowSpec = Window
                .partitionBy("player_id", "quarter", "game_id", "EVENTNUM")
                .orderBy("original_order");

            // Window for future values – from the next row to the end. It will be used for an edge case
            WindowSpec futureWindow = Window
                .partitionBy("player_id", "quarter", "game_id", "EVENTNUM")
                .orderBy("original_order")
                .rowsBetween(1, Window.unboundedFollowing());

            Dataset<Row> reboundLocations = events.join(moments, 
                events.col("GAME_ID").equalTo(moments.col("game_id"))
                .and(events.col("EVENTNUM").equalTo(moments.col("event_id")))
                // Search within a window of 3 seconds from the time recorded in events
                //.and(abs(events.col("game_clock").minus(moments.col("game_clock"))).leq(5))
                .and(events.col("PLAYER1_ID").equalTo(moments.col("player_id"))))
                // Drop unecessary columns to boost performance and prevent ambiguity
                    .drop(moments.col("game_id"))
                    .drop(events.col("game_clock"))
                    .drop("PERIOD");

            // Compute the previous shot_clock value.
            Dataset<Row> reboundMoments = reboundLocations
                .withColumn("all_null_shot_clock", count(col("shot_clock")).over(windowSpec).equalTo(0))
                .withColumn("prev_shot_clock", lag(col("shot_clock"), 1).over(windowSpec))
                .withColumn("next_non_null", first(col("shot_clock"), true).over(futureWindow))
                // Filter: where the filled previous shot_clock is not 24 &
                // the current shot_clock is between 24 and 23
                // signifying a reset of the shot clock
                // (Allowing numbers between 24 and 23 because sampling may miss the 24 point)
                .filter(
                    (col("shot_clock").leq(24).and(col("shot_clock").gt(23))
                    .and(col("prev_shot_clock").notEqual(24).or(col("prev_shot_clock").isNull())))
                    .or( 
                        // Handle edge case where game_clock < 24 and therefore
                        // shot clock is NULL throughout the whole window
                        col("all_null_shot_clock").equalTo(true)
                        .and(col("game_clock").lt(24))
                        )
                    .or(
                        // Handle another edge case wherethere are valid shot_clocks
                        // inside the window but the clock then becomes null due to rebound
                        col("shot_clock").isNull()
                        .and(col("prev_shot_clock").isNotNull())
                        .and(col("game_clock").lt(24))
                        .and(col("next_non_null").isNull())
                    )
                );

            // Join ball position to candidate player positions
            Dataset<Row> reboundWithBall = reboundMoments.join(ballMoments,
                reboundMoments.col("game_id").equalTo(ballMoments.col("ball_game_id"))
                .and(reboundMoments.col("quarter").equalTo(ballMoments.col("ball_quarter")))
                .and(reboundMoments.col("event_id").equalTo(ballMoments.col("ball_event_id")))
                .and(reboundMoments.col("game_clock").equalTo(ballMoments.col("ball_game_clock")))
                // This approach also handles edge case where shot_clock is null.
                // If left untreated join fails
                .and(coalesce(reboundMoments.col("shot_clock"), lit(-1))
                        .equalTo(coalesce(ballMoments.col("ball_shot_clock"), lit(-1))))
            )
            .drop("event_id", "ball_game_id", "ball_quarter", "ball_event_id", "ball_game_clock", "ball_shot_clock");

            // For every candidate calculate player - ball distance 
            Dataset<Row> withBallDistance = reboundWithBall.withColumn("distance",
                sqrt(
                    pow(col("x_loc").minus(col("ball_x")), 2)
                .plus(pow(col("y_loc").minus(col("ball_y")), 2))
                )
            );
            // Choose candidates that have less than 1 meters player - ball dist 
            double threshold = 1.0;
            Dataset<Row> filteredRebounds = withBallDistance.filter(col("distance").leq(threshold));
            // pick candidate with smallest distance 

            Dataset<Row> bestRebound = filteredRebounds
                .withColumn("rn", row_number().over(windowSpec))
                .filter(col("rn").equalTo(1))
                .drop("rn");
            
            // Now that we have the optimal rebound moments calculate distance 
            // of player from hoops. The minimum distance is the actual rebound
            // distance
            Dataset<Row> withDistance = bestRebound
                .withColumn("distance_hoop1", sqrt(pow(col("x_loc").minus(lit(1.6764)), 2)
                                                .plus(pow(col("y_loc").minus(lit(7.62)), 2))))
                .withColumn("distance_hoop2", sqrt(pow(col("x_loc").minus(lit(26.9748)), 2)
                                                .plus(pow(col("y_loc").minus(lit(7.62)), 2))))
                .withColumn("distance_to_hoop", least(col("distance_hoop1"), col("distance_hoop2")));
            
            // Get the furthest rebound for every player
            Dataset<Row> furthestRebound = withDistance.groupBy("player_id")
                .agg(
                     max("distance_to_hoop").alias("furthest_distance")
                );
            // Join result with total rebound info
            Dataset<Row> result = playerRebounds.join(furthestRebound,
                furthestRebound.col("player_id").equalTo(playerRebounds.col("PLAYER1_ID")))
                .drop("player_id");
            
            // Used during development cycle: Informs on rebounds not found/ processed 
            // in data.
            Dataset<Row> missingRebounds = events
                .join(withDistance, 
                    events.col("GAME_ID").equalTo(withDistance.col("GAME_ID"))
                    .and(events.col("EVENTNUM").equalTo(withDistance.col("EVENTNUM"))), 
                    "left_anti");
            
            // Write output
            result.coalesce(1)
                .write()
                .mode("overwrite")
                .option("header", false)
                .option("delimiter", " ")
                .csv("hdfs:///user/r1017513/ReboundsPerPlayer");
            
            // Write output
            teamAvgRebounds.coalesce(1)
                .write()
                .mode("overwrite")
                .option("header", false)
                .option("delimiter", " ")
                .csv("hdfs:///user/r1017513/ReboundsPerTeam");

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