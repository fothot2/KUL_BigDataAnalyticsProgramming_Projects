import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        int lognumbits = Integer.parseInt(args[0]);
        int k = Integer.parseInt(args[1]); 

        // read the elements in file 
        // Create a BloomFilter with 10 bits and 3 hash functions
        BloomFilter bloomFilter = new BloomFilter(lognumbits, k);
        
        // read the elememt in /data/users.txt and add them to the bloom filter
        try {
            File file = new File("data/users.txt");
            
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                bloomFilter.add(line);
            }
            reader.close();
        } catch (IOException e) {
            System.out.println("An error occurred while reading the file.");
        }
        int falsePositives = 0;
        try {
            File file = new File("data/fake_users.txt");
            
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                if(bloomFilter.check(line)){
                    falsePositives ++;
                }
            }
            reader.close();
        } catch (IOException e) {
            System.out.println("An error occurred while reading the file.");
        }
        System.out.println(falsePositives);


        // Check if some strings are in the BloomFilter
        //System.out.println(bloomFilter.check("Angelo00"));   // true
        //System.out.println(bloomFilter.check("ItzGarcyy"));  // true
        //System.out.println(bloomFilter.check("ShadowMystorm"));  // false
        //System.out.println(bloomFilter.check("Shad3454556"));  // false
        //System.out.println(bloomFilter.check("ShadowMystorm1234"));   // false
    }
}

