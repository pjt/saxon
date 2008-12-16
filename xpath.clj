#!/usr/bin/env clj
; Command-line Saxon Xpath evaluator
(use 'saxon '[clojure.contrib.str-utils :only (str-join)])

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
            (let [result (if (coll? (first result)) (apply concat result) result)]
                (println (str-join "\n" result)))
            (println (str result)))))
            
