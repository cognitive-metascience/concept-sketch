$env:JAVA_HOME="C:/Program Files/Java/jdk-22"
$env:PATH="$env:JAVA_HOME/bin;$env:PATH"
mvn -f "d:/git/word-sketch-lucene/pom.xml" clean compile test -Dtest=CQLParserTest
