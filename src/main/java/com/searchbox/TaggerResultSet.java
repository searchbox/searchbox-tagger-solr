/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.searchbox;

import java.util.TreeSet;

/**
 *
 * @author andrew
 */
class TaggerResultSet{
    public TreeSet<TaggerResult> suggestions;
    private int maxTreeSize=5;
    

    TaggerResultSet(int maxTreeSize) {
        suggestions=new TreeSet();
        this.maxTreeSize=maxTreeSize;
    }
    
    public void add(String phrase, double score){
        suggestions.add(new TaggerResult(phrase, score));
        if(maxTreeSize!=-1){
        if(suggestions.size()>maxTreeSize){
            suggestions.remove(suggestions.last());
        }
        }
    }
    
    public class TaggerResult implements Comparable<TaggerResult>{
            String suggestion;
            Double score;

        private TaggerResult(String phrase, double score) {
            this.suggestion=phrase;
            this.score=score;
        }

        public int compareTo(TaggerResult o) {
            int retval=o.score.compareTo(this.score);
            if(retval==0){
               retval=o.suggestion.compareTo(this.suggestion);
            }
            
            return  retval;
        }
        
    }
}
