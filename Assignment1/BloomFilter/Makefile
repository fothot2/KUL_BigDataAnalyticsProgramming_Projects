# Define the target files
TARGETS = ./bin/Main.class ./bin/BloomFilter.class ./bin/MurmurHash.class 

# Define the default target
default: $(TARGETS)

# Compile Main.java, BloomFilter.java, and MurmurHash.java
compile: ./src/Main.java ./src/BloomFilter.java ./src/MurmurHash.java
	rm -rf ./bin
	mkdir -p ./bin
	javac -d ./bin ./src/Main.java ./src/BloomFilter.java ./src/MurmurHash.java

# Run the code in Main.java with arguments
run: default
	java -cp ./bin/ Main $(lognumBits) $(k)

# Clean the compiled files
clean:
	rm -rf ./bin/$(TARGETS)


