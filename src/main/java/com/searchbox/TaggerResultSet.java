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

import java.util.TreeSet;

/**
 * 
 * @author andrew
 */
class TaggerResultSet {
  public TreeSet<TaggerResult> suggestions;
  private int maxTreeSize = 5;

  TaggerResultSet(int maxTreeSize) {
    suggestions = new TreeSet();
    this.maxTreeSize = maxTreeSize;
  }

  public void add(String phrase, double score) {
    suggestions.add(new TaggerResult(phrase, score));
    if (maxTreeSize != -1) {
      if (suggestions.size() > maxTreeSize) {
        suggestions.remove(suggestions.last());
      }
    }
  }

  public class TaggerResult implements Comparable<TaggerResult> {
    String suggestion;
    Double score;

    private TaggerResult(String phrase, double score) {
      this.suggestion = phrase;
      this.score = score;
    }

    public int compareTo(TaggerResult o) {
      int retval = o.score.compareTo(this.score);
      if (retval == 0) {
        retval = o.suggestion.compareTo(this.suggestion);
      }

      return retval;
    }

  }
}
