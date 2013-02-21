/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.searchbox;

/**
 *
 * @author andrew
 */
public class TaggerComponentParams {

    
    public static final long serialVersionUID = 42L;
    public static String PRODUCT_NAME = "sbtagger";
    public static String PRODUCT_KEY = "tag";
    
    public static final String BUILD_ON_OPTIMIZE = "buildOnOptimize";
    public static final String BUILD_ON_OPTIMIZE_DEFAULT = "false";
    
    public static final String BUILD_ON_COMMIT = "buildOnCommit";
    public static final String BUILD_ON_COMMIT_DEFAULT = "false";
    
    public static String QUERY_FIELDS = "queryFields";
    public static String QUERY_FIELD = "queryField";
    
    public static final String STOREDIR = "storeDir";
    public static final String STOREDIR_DEFAULT = PRODUCT_NAME;
    
    
    public static final String BUILD = "build";
    
    public static final String QUERY = "q";
    
    public static final String COUNT = "count";
    public static final Integer COUNT_DEFAULT = 5;
    
    public static final String MINDOCFREQ = "minDocFreq";
    public static final Integer MINDOCFREQ_DEFAULT = 2;
    
    public static final String MAX_PHRASE_SEARCH = "mps";
    
    public static final String MAXNUMDOCS = "maxNumDocs";
    public static final Integer MAXNUMDOCS_DEFAULT = -1;
    
}
