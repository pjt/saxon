(ns test-saxon
  (:use [clojure.test])
  (:require [saxon :as s])
  (:import (net.sf.saxon.s9api XdmNode SaxonApiException)))

(def xml "<root><a><b/></a></root>")

(deftest test-compile-xml
  (is (instance? XdmNode (s/compile-xml xml)))
  (is (instance? XdmNode (s/compile-xml (java.io.StringReader. xml))))
  (is (instance? XdmNode (s/compile-xml (java.io.ByteArrayInputStream. (.getBytes xml "UTF-8")))))
  (is (thrown? SaxonApiException (s/compile-xml (format "%s bad stuff" xml)))))

(deftest test-query
  (is (= 3 (count (s/query "//element()" (s/compile-xml xml))))))

(deftest test-node-path
  (is (= "/root/a[1]/b[1]" 
         (->> (s/compile-xml xml)
           (s/query "(//element())[3]")
           s/node-path))))
