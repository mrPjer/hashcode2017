echo "Running $1"
cat $1 | ./gradlew -q run > $1.out
