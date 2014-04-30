/*******************************************************************************
 * Copyright Searchbox - http://www.searchbox.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.searchbox;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.util.Bits;
import org.apache.solr.search.SolrIndexSearcher;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author andrew
 */
public class Tagger {

  private static org.slf4j.Logger LOGGER = LoggerFactory
      .getLogger(TaggerComponent.class);
  private String DOC_COUNTS_STRING = "****totalcounts****";
  private SentenceModel sentenceModel;
  private String sentenceDetectorModelName = "en-sent.bin";
  private TokenizerModel tokenzierModel;
  private String tokenizerModelName = "en-token.bin";
  private POSModel posModel;
  private String posModelName = "en-pos-maxent.bin";
  private String stopWordsFileName = "stopwords_for_suggestor.txt";
  private HashSet<String> stopwords;
  private HashMap<String, Double> boosts;
  public int numdocs;
  private HashMap<String, Integer> dfcounts = new HashMap<String, Integer>();
  private HashMap<String, Integer> tfcounts = new HashMap<String, Integer>(); // only
                                                                              // used
                                                                              // at
                                                                              // create
                                                                              // and
                                                                              // prune
                                                                              // time,
                                                                              // otherwise
                                                                              // null
  private static final Set<String> POS = new HashSet<String>(
      Arrays.asList(new String[] { "NN", "NNS", "NNPS", "NNP", "JJ" }));

  /*------------*/

  public void Tokenizer(String filename_model) throws FileNotFoundException {
    InputStream modelIn = (getClass().getResourceAsStream("/" + filename_model));
    try {
      tokenzierModel = new TokenizerModel(modelIn);
    } catch (IOException e) {
    }
  }

  public String[] getTokens(String fulltext) {
    TokenizerME tokenizer = new TokenizerME(tokenzierModel);
    return tokenizer.tokenize(fulltext);
  }

  /*------------*/

  /*------------*/
  public void SentenceParser(String filename_model)
      throws FileNotFoundException {
    InputStream modelIn = (getClass().getResourceAsStream("/" + filename_model));
    try {
      sentenceModel = new SentenceModel(modelIn);
    } catch (IOException e) {
    }
  }

  public String[] getSentences(String fulltext) {
    SentenceDetectorME sentenceDetector = new SentenceDetectorME(sentenceModel);
    return sentenceDetector.sentDetect(fulltext);
  }

  /*------------*/

  /*--------*/
  public void POSTagger(String filename_model) {
    InputStream modelIn = (getClass().getResourceAsStream("/" + filename_model));
    try {
      posModel = new POSModel(modelIn);
    } catch (Exception e) {
    }
  }

  public String[] getPOSTokens(String[] in) {
    POSTaggerME postagger = new POSTaggerME(posModel);
    return postagger.tag(in);
  }

  /*----------*/
  private void init(String boostsFileName) {
    try {
      SentenceParser(sentenceDetectorModelName);
      Tokenizer(tokenizerModelName);
      POSTagger(posModelName);
      loadStopWords(stopWordsFileName);
      loadBoosts(boostsFileName);
    } catch (FileNotFoundException ex) {
      LOGGER.error("File not found", ex);
    }
  }

  private void DfCountBuilder(SolrIndexSearcher searcher, String[] fields,
      int maxNumDocs) {
    IndexReader reader = searcher.getIndexReader();
    Bits liveDocs = MultiFields.getLiveDocs(reader); // WARNING: returns null if
                                                     // there are no deletions

    maxNumDocs = Math.min(maxNumDocs, reader.maxDoc());

    if (maxNumDocs == -1) {
      maxNumDocs = reader.maxDoc();
    }
    LOGGER.info("Analyzing docs:\t" + numdocs);

    for (int docID = 0; docID < reader.maxDoc(); docID++) {
      if (numdocs > maxNumDocs) {
        break;
      }
      if (liveDocs != null && !liveDocs.get(docID)) {
        continue; // deleted
      }

      if ((docID % 1000) == 0) {
        LOGGER.debug("Doing " + docID + " of " + maxNumDocs);
      }

      StringBuilder text = new StringBuilder();
      for (String field : fields) { // not sure if this is the best way, might
                                    // make sense to do a
        // process text for each field individually, but then book keeping
        // the doc freq for terms becomes a bit of a pain in the ass
        try {
          text.append(". " + reader.document(docID).get(field));
        } catch (IOException ex) {
          LOGGER.warn("Document " + docID + " missing requested field ("
              + field + ")...ignoring");
        }
      }
      if (text.length() > 0) { // might as well see if its empty
        processDocText(text.toString());
        numdocs++;

      }
    }

    LOGGER.info("Number of documents analyzed: \t" + numdocs);
    dfcounts.put(DOC_COUNTS_STRING, numdocs);
    tfcounts.put(DOC_COUNTS_STRING, numdocs);
  }

  Tagger(HashMap<String, Integer> dfcounts, String boostsFileName) {

    init(boostsFileName);
    this.dfcounts = dfcounts;
    this.numdocs = dfcounts.get(DOC_COUNTS_STRING);
  }

  Tagger(SolrIndexSearcher searcher, String[] fields, String boostsFileName,
      int minDocFreq, int minTermFreq, int maxNumDocs) {
    init(boostsFileName);
    DfCountBuilder(searcher, fields, maxNumDocs);
    pruneList(minDocFreq, minTermFreq);
  }

  private void processDocText(String text) {
    LOGGER.trace("Processing text:\t" + text);
    HashSet<String> seenTerms = new HashSet<String>();
    for (String sentence : getSentences(text)) {
      String[] tokens = getTokens(sentence); // TODO: fix this part, its a bit
                                             // of a hack but should be okay
      String[] poses = getPOSTokens(tokens);

      for (int zz = 0; zz < tokens.length; zz++) {
        String ltoken = tokens[zz].toLowerCase().trim();
        if (ltoken.length() < 4 || stopwords.contains(ltoken)
            || !POS.contains(poses[zz])) {
          continue;
        }
        seenTerms.add(ltoken);
        Integer oldcount = tfcounts.containsKey(ltoken) ? tfcounts.get(ltoken)
            : 0;
        tfcounts.put(ltoken, oldcount + 1);
      }
    }
    addSeenTermsToDFcount(seenTerms);
  }

  public TaggerResultSet tagText(String text, int lcount) {
    TaggerResultSet trs = new TaggerResultSet(lcount);
    LOGGER.trace("Tagging text:\t" + text);
    HashSet<ArrayList<String>> ngrams = new HashSet<ArrayList<String>>();
    HashMap<String, Integer> seenTerms = new HashMap<String, Integer>();

    for (String sentence : getSentences(text)) {
      String[] tokens = getTokens(sentence); // TODO: fix this part, its a bit
                                             // of a hack but should be okay
      String[] poses = getPOSTokens(tokens);

      ArrayList<String> prev = new ArrayList<String>();
      for (int zz = 0; zz < tokens.length; zz++) {
        String ltoken = tokens[zz].toLowerCase().trim();
        if (ltoken.length() < 4 || stopwords.contains(ltoken)
            || !POS.contains(poses[zz])) {
          ngrams.add(prev);
          prev = new ArrayList<String>();
          continue;
        }
        prev.add(ltoken);
        Integer count = seenTerms.containsKey(ltoken) ? seenTerms.get(ltoken)
            : 0;
        seenTerms.put(ltoken, count + 1);
      }
    }

    for (ArrayList<String> phrase : ngrams) {
      double score = 0;
      for (String term : phrase) {
        Integer Dt = dfcounts.get(term);
        if (Dt != null && Dt > 0) {
          Double boost = boosts.containsKey(term) ? boosts.get(term) : 1.0;
          score += seenTerms.get(term) * Math.log10(numdocs / (Dt * 1.0))
              * boost;
        }
      }
      trs.add(mergeArrayListSTring(phrase), score);
    }
    return trs;
  }

  private String mergeArrayListSTring(ArrayList<String> list) {
    StringBuilder sb = new StringBuilder();
    for (String s : list) {
      sb.append(s);
      sb.append(" ");
    }
    return sb.toString();
  }

  private void loadBoosts(String boostsFileName) {
    boosts = new HashMap<String, Double>();
    BufferedReader in = null;
    if (boostsFileName == null) {
      return;
    }
    try {
      String workingDir = System.getProperty("user.dir");
      LOGGER.info(workingDir);
      in = new BufferedReader(new InputStreamReader(new FileInputStream(
          boostsFileName)));
      String line;
      while ((line = in.readLine()) != null) {
        String[] wordscore = line.split("\\s+");
        boosts.put(wordscore[0].trim().toLowerCase(),
            Double.parseDouble(wordscore[1]));
      }
      in.close();
    } catch (Exception ex) {
      LOGGER
          .error("Error loading Boosts, format is word_string [tab] boost_double \t"
              + ex.getMessage());
    }
  }

  private void loadStopWords(String stopWordsFileName) {
    stopwords = new HashSet<String>();
    BufferedReader in = null;
    try {
      in = new BufferedReader(new InputStreamReader(
          (getClass().getResourceAsStream("/" + stopWordsFileName))));
      String line;
      while ((line = in.readLine()) != null) {
        stopwords.add(line.trim().toLowerCase());
      }
      in.close();
    } catch (Exception ex) {
      LOGGER.error("Error loading stopwords\t" + ex.getMessage());
    }
  }

  private void pruneList(int minDocFreq, int minTermFreq) {
    for (Iterator<Map.Entry<String, Integer>> it = dfcounts.entrySet()
        .iterator(); it.hasNext();) {
      Map.Entry<String, Integer> entry = it.next();
      Integer tfcount = tfcounts.get(entry.getKey());
      if (tfcount == null) {
        tfcount = 0;
      }
      if (entry.getValue() < minDocFreq || tfcount < minTermFreq) {
        it.remove();
      }
    }
  }

  private void addSeenTermsToDFcount(HashSet<String> seenTerms) {
    for (String term : seenTerms) {
      Integer oldcount = dfcounts.containsKey(term) ? dfcounts.get(term) : 0;
      dfcounts.put(term, oldcount + 1);
    }
  }

  public HashMap<String, Integer> getDFs() {
    return dfcounts;
  }

  void setDFs(HashMap<String, Integer> dfcounts) {
    this.dfcounts = dfcounts;
  }

  public void writeFile(File dir) {
    LOGGER.info("Writing tagger model to file");
    try {
      FileOutputStream fos = new FileOutputStream(dir + File.separator
          + "tagger.ser");
      BufferedOutputStream bos = new BufferedOutputStream(fos);
      ObjectOutputStream oos = new ObjectOutputStream(bos);
      oos.writeObject(this.dfcounts);
      oos.flush();
      oos.close();
    } catch (Exception e) {
      LOGGER
          .error("There was a problem with saving model to disk. Tagger will still work because model is in memory."
              + e.getMessage());
    }
    LOGGER.info("Done writing tagger model to file");
  }

  public static Tagger loadTagger(File dir, String boostsFileName) {
    LOGGER.info("Reading object from file");
    Tagger dfb = null;
    try {
      FileInputStream fis = new FileInputStream(dir + File.separator
          + "tagger.ser");
      BufferedInputStream bis = new BufferedInputStream(fis);
      ObjectInputStream ois = new ObjectInputStream(bis);
      HashMap<String, Integer> dfcountsin = (HashMap<String, Integer>) ois
          .readObject();
      ois.close();
      dfb = new Tagger(dfcountsin, boostsFileName);
    } catch (Exception e) {
      LOGGER
          .error("There was a problem with load model from disk. Tagger will not work unless build=true option is passed. Stack Message: "
              + e.getMessage());
    }
    LOGGER.info("Done reading object from file");

    return dfb;
  }
}
