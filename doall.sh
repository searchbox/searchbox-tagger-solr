mvn -T compile package -DskipTests=true
cp ./target/searchbox-tagger-1.0-SNAPSHOT-jar-with-dependencies.jar /salsasvn/projects/solr-4.1.0/example/solr/solrLib
cp ./target/searchbox-tagger-1.0-SNAPSHOT-jar-with-dependencies.jar /salsasvn/searchbox-server/solr/solrLib
