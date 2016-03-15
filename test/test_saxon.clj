(ns test-saxon
  (:use [clojure.test])
  (:require [saxon :as s])
  (:import (net.sf.saxon.s9api XdmNode SaxonApiException)))

(def xml "<root><a><b/></a></root>")
(def proc (s/processor))

(deftest processor-allocation
  (is (not (nil? proc)))
  (is (not (nil? (s/processor {:allow-multithreading true}))))
  (is (not (nil? (s/processor :allow-multithreading true))))
  (is (not (nil? (s/processor :allow-multithreading true :default-language "EN")))))


(deftest test-compile-xml
  (is (instance? XdmNode (s/compile-xml proc xml)))
  (is (instance? XdmNode (s/compile-xml proc (java.io.StringReader. xml))))
  (is (instance? XdmNode (s/compile-xml proc (java.io.ByteArrayInputStream. (.getBytes xml "UTF-8")))))
  (is (thrown? SaxonApiException (s/compile-xml proc (format "%s bad stuff" xml)))))

(deftest test-query
  (is (= 3 (count (s/query proc "//element()" (s/compile-xml proc xml))))))

(deftest test-node-path
  (is (= "/root/a[1]/b[1]" 
         (->> (s/compile-xml proc xml)
           (s/query proc "(//element())[3]")
           s/node-path))))

(def xmldoc  (s/compile-xml proc (java.io.File. "test/xhtmldoc.html") ))
(deftest readme-examples
  (is (=
        (into #{} (s/query proc "distinct-values(//element()/local-name())" xmldoc))
        #{"html" "head" "meta" "body" "div" "table" "tbody" "tr" "td" "span" "br" "a" "foo"}))
  (is (=
        (into #{} (s/query proc "distinct-values(//xhtml:*/local-name())" {:xhtml "http://www.w3.org/1999/xhtml"} xmldoc))
        #{"html" "head" "meta" "body" "div" "table" "tbody" "tr" "td" "span" "br" "a"}))
  )
