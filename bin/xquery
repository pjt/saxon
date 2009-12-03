#!/usr/bin/env clj
; Command-line Saxon Xpath evaluator
(use 'saxon '[clojure.contrib.str-utils :only (str-join)]
            '[clojure.contrib.seq-utils :only (flatten)])

(let [args *command-line-args*
      cnt  (count args)]
    (if-let 
        [result
            (cond 
                (zero? cnt)
                    (println "args: [xml*] xpath2.0-expression")
                (= 1 cnt)
                    ((compile-xpath (first args)) (compile-string System/in))
                :else
                    (map (compile-xpath (last args)) 
                                (map compile-file (butlast args))))]
        (if (coll? result)
            (println (str-join "\n" (flatten (remove nil? result))))
            (println (str result)))))
            
