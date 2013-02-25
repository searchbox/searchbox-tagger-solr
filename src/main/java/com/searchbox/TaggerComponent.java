/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.searchbox;

import com.searchbox.TaggerResultSet.TaggerResult;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javax.xml.ws.Response;
import org.apache.lucene.document.Document;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrEventListener;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author andrew
 */
public class TaggerComponent extends SearchComponent implements SolrCoreAware, SolrEventListener {

    private static Logger LOGGER = LoggerFactory.getLogger(TaggerComponent.class);
    protected NamedList initParams;
    protected File storeDir;
    protected String storeDirname;
    protected String boostsFileName;
    protected Boolean buildOnOptimize = false;
    protected Boolean buildOnCommit = false;
    protected Integer minDocFreq;
    protected Integer minTermFreq;
    protected Integer maxNumDocs;
    private Tagger dfb;
    volatile long numRequests;
    volatile long numErrors;
    volatile long totalBuildTime;
    volatile long totalRequestsTime;
    volatile String lastbuildDate;
    private boolean keystate = true;
    List<String> globalfields;

    @Override
    public void init(NamedList args) {
        LOGGER.debug(("Hit init"));

        super.init(args);
        this.initParams = args;


        LOGGER.trace("Checking license");
        /*--------LICENSE CHECK ------------ */
        String key = (String) args.get("key");
        if (key == null) {
            keystate = false;
            throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
                    "Need to specify license key using <str name=\"key\"></str>.\n If you don't have a key email contact@searchbox.com to obtain one.");
        }
        if (!checkLicense(key, TaggerComponentParams.PRODUCT_KEY)) {
            keystate = false;
            throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
                    "License key is not valid for this product, email contact@searchbox.com to obtain one.");
        }

        LOGGER.trace("Done checking license");
        /*--------END LICENSE CHECK ------------ */

        buildOnOptimize = Boolean.parseBoolean((String) args.get(TaggerComponentParams.BUILD_ON_OPTIMIZE));
        if (buildOnOptimize == null) {
            buildOnOptimize = Boolean.parseBoolean(TaggerComponentParams.BUILD_ON_OPTIMIZE_DEFAULT);
        }

        buildOnCommit = Boolean.parseBoolean((String) args.get(TaggerComponentParams.BUILD_ON_COMMIT));
        if (buildOnCommit == null) {
            buildOnCommit = Boolean.parseBoolean(TaggerComponentParams.BUILD_ON_COMMIT_DEFAULT);
        }

        storeDirname = (String) args.get(TaggerComponentParams.STOREDIR);
        if (storeDirname == null) {
            storeDirname = TaggerComponentParams.STOREDIR_DEFAULT;
        }

        minDocFreq = (Integer) args.get(TaggerComponentParams.MINDOCFREQ);
        if (minDocFreq == null) {
            minDocFreq = TaggerComponentParams.MINDOCFREQ_DEFAULT;
        }

        minTermFreq = (Integer) args.get(TaggerComponentParams.MINTERMFREQ);
        if (minTermFreq == null) {
            minTermFreq = TaggerComponentParams.MINTERMFREQ_DEFAULT;
        }
        

        maxNumDocs = (Integer) args.get(TaggerComponentParams.MAXNUMDOCS);
        if (maxNumDocs == null) {
            maxNumDocs = TaggerComponentParams.MAXNUMDOCS_DEFAULT;
        }

        globalfields = ((NamedList) args.get(TaggerComponentParams.QUERY_FIELDS)).getAll(TaggerComponentParams.QUERY_FIELD);
        if (globalfields == null) {
            throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
                    "Need to specify at least one field");
        }

        boostsFileName = (String) args.get(TaggerComponentParams.BOOSTS_FILENAME);



        LOGGER.debug("maxNumDocs is " + maxNumDocs);
        LOGGER.debug("minDocFreq is " + minDocFreq);
        LOGGER.debug("buildOnCommit is " + buildOnCommit);
        LOGGER.debug("buildOnOptimize is " + buildOnOptimize);
        LOGGER.debug("storeDirname is " + storeDirname);
        LOGGER.debug("Fields is " + globalfields);
        LOGGER.debug("Boosts file is " + boostsFileName);

    }

    @Override
    public void prepare(ResponseBuilder rb) throws IOException {
        //none necessary
    }

    @Override
    public void process(ResponseBuilder rb) throws IOException {
        LOGGER.trace(("Hit process"));
        numRequests++;

        if (!keystate) {
            LOGGER.error("License key failure, not performing tagging. Please email contact@searchbox.com for more information.");
            numErrors++;
            return;
        }
        SolrParams params = rb.req.getParams();

        boolean build = params.getBool(TaggerComponentParams.PRODUCT_NAME + "." + TaggerComponentParams.BUILD, false);
        SolrIndexSearcher searcher = rb.req.getSearcher();
        if (build) {
            long lstartTime = System.currentTimeMillis();
            buildAndWrite(searcher);
            totalBuildTime += System.currentTimeMillis() - lstartTime;
            lastbuildDate = new Date().toString();
        }

        if (dfb == null) {
            throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
                    "Model for SBtagger not created, create using sbtagger.build=true");
        }
        String commonparamsQuery = params.get(CommonParams.Q);
        String query = params.get(TaggerComponentParams.PRODUCT_NAME + "." + TaggerComponentParams.QUERY);

        int lcount = params.getInt(TaggerComponentParams.PRODUCT_NAME + "." + TaggerComponentParams.COUNT, TaggerComponentParams.COUNT_DEFAULT);
        
        LOGGER.debug("Tagger Query:\t" + query);
        LOGGER.debug("Common params Query:\t" + commonparamsQuery);

        long lstartTime = System.currentTimeMillis();

        NamedList response = null;
        if (commonparamsQuery != null) {
            //do for documents
            response = doDocuments(rb, params, searcher,lcount);
        } else if (query != null) {
            //do for tag text
            response = doText(query, lcount);
        } else {
            LOGGER.warn("No query in q or sbtagger.q, returning..maybe was just used for  building index?");
            numErrors++;
            return;
        }


        rb.rsp.add(TaggerComponentParams.PRODUCT_NAME, response);
        totalRequestsTime += System.currentTimeMillis() - lstartTime;
    }

    private NamedList doDocuments(ResponseBuilder rb, SolrParams params, SolrIndexSearcher searcher, int lcount) {
        /*-----------------*/


        String[] localfields = params.getParams(TaggerComponentParams.QUERY_FIELDS);
        List<String> fields = null;

        if (globalfields != null) {
            fields = globalfields;
        }
        if (localfields != null) {
            fields = new ArrayList<String>(Arrays.asList(localfields));
        }

        if (fields == null) {
            LOGGER.error("Fields aren't defined, not performing tagging.");
            return null;
        }

        

        DocList docs = rb.getResults().docList;
        if (docs == null || docs.size() == 0) {
            LOGGER.debug("No results");
        }
        LOGGER.debug("Doing This many docs:\t" + docs.size());

        Set<String> fset = new HashSet<String>();

        SchemaField keyField = rb.req.getCore().getSchema().getUniqueKeyField();
        if (null != keyField) {
            fset.add(keyField.getName());
        }
        for (String field : fields) {
            fset.add(field);
        }

        NamedList response = new SimpleOrderedMap();


        DocIterator iterator = docs.iterator();
        for (int i = 0; i < docs.size(); i++) {
            try {
                int docId = iterator.nextDoc();

                Document doc = searcher.doc(docId, fset);
                StringBuilder sb = new StringBuilder();
                for (String field : fields) {
                    String text = doc.getField(field).stringValue();
                    sb.append(text + ". ");
                }

                String q = sb.toString();
                String id = doc.getField(keyField.getName()).stringValue();
                //do work here
                TaggerResultSet trs = dfb.tagText(q, lcount);
                NamedList docresponse = new SimpleOrderedMap();
                for (TaggerResult tr : trs.suggestions) {
                    docresponse.add(tr.suggestion, tr.score);
                }
                response.add(id, docresponse);
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(TaggerComponent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        //   response.add(suggestion.suggestion, suggestion.probability);
        return response;
    }

    private NamedList doText(String q, int lcount) {
        NamedList response = new SimpleOrderedMap();

        TaggerResultSet trs = dfb.tagText(q, lcount);
        NamedList docresponse = new SimpleOrderedMap();
        for (TaggerResult tr : trs.suggestions) {
            docresponse.add(tr.suggestion, tr.score);
        }
        response.add("sbtagger.q", docresponse);
        return response;
    }

    public void inform(SolrCore core) {
        LOGGER.trace(("Hit inform"));


        if (storeDirname != null) {
            storeDir = new File(storeDirname);
            if (!storeDir.isAbsolute()) {
                storeDir = new File(core.getDataDir() + File.separator + storeDir); //where does core come from?!
            }
            if (!storeDir.exists()) {
                LOGGER.warn("Directory " + storeDir.getAbsolutePath() + " doesn't exist for re-load of tagger, creating emtpy directory, make sure to use sbtagger.build before first use!");
                storeDir.mkdirs();
            } else {
                try {
                    dfb = Tagger.loadTagger(storeDir, boostsFileName);
                } catch (Exception ex) {
                    LOGGER.error("Error loading Tagger model");
                }
            }
        }

        if (buildOnCommit || buildOnOptimize) {
            LOGGER.info("Registering newSearcher listener for Searchbox Tagger: ");
            core.registerNewSearcherListener(this);
        }
    }

    @Override
    public String getDescription() {
        return "Searchbox Suggester";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getSource() {
        return "http://www.searchbox.com";
    }

    @Override
    public NamedList<Object> getStatistics() {

        NamedList all = new SimpleOrderedMap<Object>();
        all.add("requests", "" + numRequests);
        all.add("errors", "" + numErrors);
        all.add("totalBuildTime(ms)", "" + totalBuildTime);
        all.add("totalRequestTime(ms)", "" + totalRequestsTime);
        if (lastbuildDate == null) {
            lastbuildDate = "N/A";
        }
        all.add("lastBuildDate", lastbuildDate);

        return all;
    }

    public void postCommit() {
        LOGGER.trace("postCommit hit");

    }

    public void postSoftCommit() {
        LOGGER.trace("postSoftCommit hit");

    }

    public void newSearcher(SolrIndexSearcher newSearcher, SolrIndexSearcher currentSearcher) {
        LOGGER.trace("newSearcher hit");
        if (currentSearcher == null) {
            // firstSearcher event
            try {
                LOGGER.info("Loading tagger model.");
                dfb = Tagger.loadTagger(storeDir, boostsFileName);

            } catch (Exception e) {
                LOGGER.error("Exception in reloading tagger model.");
            }
        } else {
            // newSearcher event
            if (buildOnCommit) {
                buildAndWrite(newSearcher);
            } else if (buildOnOptimize) {
                if (newSearcher.getIndexReader().leaves().size() == 1) {
                    buildAndWrite(newSearcher);
                } else {
                    LOGGER.info("Index is not optimized therefore skipping building tagger index");
                }
            }
        }
    }

    private boolean checkLicense(String key, String PRODUCT_KEY) {
        return com.searchbox.utils.DecryptLicense.checkLicense(key, PRODUCT_KEY);
    }

    private void buildAndWrite(SolrIndexSearcher searcher) {
        LOGGER.info("Building tagger model");
        dfb = new Tagger(searcher, globalfields, boostsFileName, minDocFreq, minTermFreq, maxNumDocs);
        dfb.writeFile(storeDir);
        LOGGER.info("Done building and storing tagger model");
    }
}
