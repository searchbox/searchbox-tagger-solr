/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.searchbox;

import java.io.File;
import java.util.List;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaggerHandler extends RequestHandlerBase {

    private static Logger LOGGER = LoggerFactory.getLogger(TaggerHandler.class);
    volatile long numRequests;
    volatile long totalTime;
    volatile long numErrors;
    private boolean keystate=true;
    protected NamedList initParams;
    protected File storeDir;
    protected String storeDirname;
    protected String boostsFileName;
    private Tagger dfb;

    @Override
    public void init(NamedList args) {
        LOGGER.debug(("Hit init"));


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

        storeDirname = (String) args.get(TaggerComponentParams.STOREDIR);
        if (storeDirname == null) {
            storeDirname = TaggerComponentParams.STOREDIR_DEFAULT;
        }


        boostsFileName = (String) args.get(TaggerComponentParams.BOOSTS_FILENAME);


        LOGGER.debug("storeDirname is " + storeDirname);
        LOGGER.debug("Boosts file is " + boostsFileName);

        try {
            dfb = Tagger.loadTagger(storeDir, boostsFileName);
        } catch (Exception e) {
            throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
                    "Model doesn't exist for tagger, have you already build it using sbtagger.build=true in the searchcomponent?");
        }



        super.init(args);
    }

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
        if (!keystate) {
            LOGGER.error("License key failure, not performing tagging. Please email contact@searchbox.com for more information.");
            return;
        }

        numRequests++;
        long startTime = System.currentTimeMillis();

        try {

            SolrParams params = req.getParams();
            String q = params.get(CommonParams.Q);

            int count = params.getInt(TaggerComponentParams.COUNT, TaggerComponentParams.COUNT_DEFAULT);

            NamedList<String> results = new NamedList<String>();

            TaggerResultSet trs = dfb.tagText(q, count);

            for (TaggerResultSet.TaggerResult tr : trs.suggestions) {
                results.add(tr.suggestion, tr.score + "");
            }

            rsp.add(TaggerComponentParams.PRODUCT_NAME, results);

        } catch (Exception e) {
            numErrors++;
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e);
        } finally {
            totalTime += System.currentTimeMillis() - startTime;
        }
    }

    //////////////////////// SolrInfoMBeans methods //////////////////////
    @Override
    public String getDescription() {
        return "Searchbox Snippet";
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
        all.add("totalTime(ms)", "" + totalTime);
        return all;
    }

    private boolean checkLicense(String key, String PRODUCT_KEY) {
        return com.searchbox.utils.DecryptLicense.checkLicense(key, PRODUCT_KEY);
    }
}
