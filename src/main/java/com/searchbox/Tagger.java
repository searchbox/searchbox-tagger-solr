/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.searchbox;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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

    private static org.slf4j.Logger LOGGER = LoggerFactory.getLogger(TaggerComponent.class);
    private String DOC_COUNTS_STRING = "****totalcounts****";
    private SentenceModel sentenceModel;
    private String sentenceDetectorModelName = "en-sent.bin";
    private TokenizerModel tokenzierModel;
    private String tokenizerModelName = "en-token.bin";
    private POSModel posModel;
    private String posModelName = "en-pos-maxent.bin";
    private String stopWordsFileName = "stopwords_for_suggestor.txt";
    private HashSet<String> stopwords;
    public int numdocs;
    private HashMap<String, Integer> dfcounts = new HashMap<String, Integer>();
    ;
    private static final Set<String> POS = new HashSet<String>(Arrays.asList(new String[]{"NN", "NNS", "NNPS", "NNP", "JJ"}));

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
    public void SentenceParser(String filename_model) throws FileNotFoundException {
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
    private void init() {
        try {
            SentenceParser(sentenceDetectorModelName);
            Tokenizer(tokenizerModelName);
            POSTagger(posModelName);
            loadStopWords(stopWordsFileName);
        } catch (FileNotFoundException ex) {
            LOGGER.error("File not found", ex);
        }
    }

    private void DfCountBuilder(SolrIndexSearcher searcher, List<String> fields, int maxNumDocs) {
        IndexReader reader = searcher.getIndexReader();
        Bits liveDocs = MultiFields.getLiveDocs(reader); //WARNING: returns null if there are no deletions

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
                continue;               //deleted
            }

            if ((docID % 1000) == 0) {
                LOGGER.debug("Doing " + docID + " of " + maxNumDocs);
            }

            StringBuilder text = new StringBuilder();
            for (String field : fields) {       //not sure if this is the best way, might make sense to do a 
                //process text for each field individually, but then book keeping 
                //the doc freq for terms becomes a bit of a pain in the ass
                try {
                    text.append(". " + reader.document(docID).get(field));
                } catch (IOException ex) {
                    LOGGER.warn("Document " + docID + " missing requested field (" + field + ")...ignoring");
                }
            }
            if (text.length() > 0) { //might as well see if its empty
                processDocText(text.toString());
                numdocs++;

            }
        }

        LOGGER.info("Number of documents analyzed: \t" + numdocs);
        dfcounts.put(DOC_COUNTS_STRING, numdocs);

    }

    Tagger(HashMap<String, Integer> dfcounts) {
        init();
        this.dfcounts = dfcounts;
        this.numdocs = dfcounts.get(DOC_COUNTS_STRING);
    }

    Tagger(SolrIndexSearcher searcher, List<String> fields, int minDocFreq, int maxNumDocs) {
        init();
        DfCountBuilder(searcher, fields, maxNumDocs);
        pruneList(minDocFreq);
    }

    private void processDocText(String text) {
        LOGGER.trace("Processing text:\t" + text);
        HashSet<String> seenTerms = new HashSet<String>();
        for (String sentence : getSentences(text)) {
            String[] tokens = getTokens(sentence); //TODO: fix this part, its a bit of a hack but should be okay
            String[] poses = getPOSTokens(tokens);

            for (int zz = 0; zz < tokens.length; zz++) {
                String ltoken=tokens[zz].toLowerCase().trim();
                if (ltoken.length()< 4 || stopwords.contains(ltoken) || !POS.contains(poses[zz])) {
                    continue;
                }
                seenTerms.add(ltoken);
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
            String[] tokens = getTokens(sentence); //TODO: fix this part, its a bit of a hack but should be okay
            String[] poses = getPOSTokens(tokens);

            ArrayList<String> prev = new ArrayList<String>();
            for (int zz = 0; zz < tokens.length; zz++) {
                String ltoken = tokens[zz].toLowerCase().trim();
                if (ltoken.length()< 4 || stopwords.contains(ltoken) || !POS.contains(poses[zz])) {
                    ngrams.add(prev);
                    prev = new ArrayList<String>();
                    continue;
                }
                prev.add(ltoken);
                Integer count = seenTerms.containsKey(ltoken) ? seenTerms.get(ltoken) : 0;
                seenTerms.put(ltoken, count + 1);
            }
        }



        for (ArrayList<String> phrase : ngrams) {
            double score = 0;
            for (String term : phrase) {
                Integer Dt = dfcounts.get(term);
                if (Dt != null && Dt > 0) {
                    score += seenTerms.get(term) * Math.log10(numdocs / (Dt * 1.0));
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

    private void loadStopWords(String stopWordsFileName) {
        stopwords = new HashSet<String>();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader((getClass().getResourceAsStream("/" + stopWordsFileName))));
            String line;
            while ((line = in.readLine()) != null) {
                stopwords.add(line.trim().toLowerCase());
            }
            in.close();
        } catch (Exception ex) {
            LOGGER.error("Error loading stopwords\t" + ex.getMessage());
        }
    }

    private void pruneList(int minDocFreq) {
        for (Iterator<Map.Entry<String, Integer>> it = dfcounts.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Integer> entry = it.next();
            if (entry.getValue() < minDocFreq) {
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
}
