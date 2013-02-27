mvn -T compile package -DskipTests=true
mkdir ../deploy-dir/searchbox-tagger
cp ./target/searchbox-tagger-1.0-SNAPSHOT-jar-with-dependencies.jar  ../deploy-dir/searchbox-tagger/searchbox-tagger-1.38.jar
cp README*  ../deploy-dir/searchbox-tagger
cd ../deploy-dir/searchbox-tagger
zip searchbox-tagger.zip *
mv *.zip ..
cd ..
rm -rf searchbox-tagger/
cd /salsasvn/searchbox-tagger

