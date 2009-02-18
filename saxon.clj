; Copyright (c) December 2008, Perry Trolard, Washington University in Saint Louis.
; 
; The use and distribution terms for this software are covered by the MIT 
; Licence (http://opensource.org/licenses/mit-license.php), which can be found 
; in the file MIT.txt at the root of this distribution. Use of the software 
; counts as agreeing to be bound by the terms of this license. You must not 
; remove this notice from this software.

(ns saxon
    "Clojure Saxon wrapper"
    (:import 
        (java.io File InputStream Reader StringReader)
        (javax.xml.transform.stream StreamSource)
        (javax.xml.transform Source)
        (net.sf.saxon.s9api Axis Destination Processor Serializer 
                            Serializer$Property XPathCompiler XPathSelector 
                            XdmDestination XdmValue XdmItem XdmNode XdmNodeKind 
                            XdmAtomicValue XQueryCompiler XQueryEvaluator QName)
        (net.sf.saxon.om Navigator NodeInfo)))

;;
;; Private functions
;;

(defn- get-proc
    "Returns the Saxon Processor object, the thread-safe 
    generator class for documents, stylesheets, & XPaths.
    Creates & defs the Processor if not already created."
    {:tag Processor}
    []
    (defonce #^{:private true} *p* (Processor. false)) *p*)

(defmulti xml-source class)
  (defmethod xml-source File
    [f]
    (StreamSource. #^File f))
  (defmethod xml-source InputStream
    [i]
    (StreamSource. #^InputStream i))
  (defmethod xml-source Reader
    [r]
    (StreamSource. #^Reader r))
  (defmethod xml-source String
    [s]
    (StreamSource. (StringReader. #^String s)))
  (defmethod xml-source XdmNode
    [nd]
    (.asSource #^XdmNode nd))


;; Well, except this is public -- maybe doesn't need to be
(defn atomic?
    "Returns true if XdmItem or a subclass
    (XdmAtomicValue, XdmNode) is an atomic value."
    [#^XdmItem val]
    (.isAtomicValue val))

(defn- unwrap-xdm-items
    "Makes XdmItems Clojure-friendly. A Saxon XdmItem is either 
    an atomic value (number, string, URI) or a node. 
    
    This function returns an unwrapped item or a sequence of them, 
    turning XdmAtomicValues into their corresponding Java datatypes 
    (Strings, the numeric types), leaving XdmNodes as nodes."
    [sel]
    (let [result 
            (map #(if (atomic? %) (.getValue #^XdmAtomicValue %) %)
               sel)]
      (if (next result)
         result
         (first result))))

;;
;; Public functions
;;

(defn compile-file
    "Compiles XML file into an XdmNode, the Saxon 
    currency for in-memory tree representation. Takes
    File, InputStream, Reader; if given String, converts
    it to File."
    {:tag XdmNode}
    [f]
    (let [f     (if (string? f) (File. #^String f) f)
          strm  (xml-source f)]
      (.. (get-proc) (newDocumentBuilder) 
                      (build #^Source strm))))

(defn compile-string
    "Compiles XML string into an XdmNode, the Saxon currency 
    for in-memory tree representation. Takes string, or, 
    optionally, java.io.InputStream or java.io.Reader."
    {:tag XdmNode}
    [s]
    (.. (get-proc) (newDocumentBuilder) 
                        (build #^Source (xml-source s))))

(defn compile-xslt
    "Compiles stylesheet (from anything convertible to javax.
    xml.transform.Source), returns function that applies it to 
    compiled doc or node."
    [f]
    (let    [cmplr  (.newXsltCompiler (get-proc))
             exe    (.compile cmplr #^Source (xml-source f))]

        (fn [#^XdmNode xml & params]
            (let    [xdm-dest    (XdmDestination.)
                     transformer (.load exe)] ; created anew, is thread-safe
                (when params
                    (let [prms  (first params)
                          ks    (keys prms)]
                        (doseq [k ks]
                            (.setParameter transformer
                                (QName. #^String (name k))
                                (XdmAtomicValue. (k prms))))))
                (doto transformer
                    (.setInitialContextNode xml)
                    (.setDestination xdm-dest)
                    (.transform))
                (.getXdmNode xdm-dest)))))

; helper for compile-xpath
(defn- add-ns-to-xpath
    "Adds namespaces to XPathCompiler from map. Returns same 
    XPathCompiler."
    {:tag XPathCompiler}
    [#^XPathCompiler xp-compiler ns-map]
    (doseq [[pre uri] ns-map]
        (.declareNamespace xp-compiler (name pre) uri))
    xp-compiler)
        
(defn compile-xpath
    "Compiles XPath expression (given as string), returns
    function that applies it to compiled doc or node. Takes 
    optional map of prefixes (as keywords) and namespace URIs."
    [#^String xpath & ns-map]
    (let    [cmplr  (.newXPathCompiler (get-proc)) 
             cmplr  (if ns-map 
                        (add-ns-to-xpath cmplr (first ns-map)) 
                        cmplr)
             exe    (.compile cmplr xpath)
             selector (.load exe)]

        (fn [#^XdmNode xml] 
            (.setContextItem selector xml)
            (unwrap-xdm-items selector))))

; helper for compile-xquery
(defn- add-ns-to-xquery
    "Adds namespaces to XQueryCompiler from map. Returns same 
    XQueryCompiler."
    {:tag XQueryCompiler}
    [#^XQueryCompiler xq-compiler ns-map]
    (doseq [[pre uri] ns-map]
        (.declareNamespace xq-compiler (name pre) uri))
    xq-compiler)

(defn compile-xquery
    "Compiles XQuery expression (given as string), returns
    function that applies it to compiled doc or node. Takes 
    optional map of prefixes (as keywords) and namespace URIs."
    [#^String xquery & ns-map]
    (let    [cmplr  (.newXQueryCompiler (get-proc)) 
             cmplr  (if ns-map 
                        (add-ns-to-xquery cmplr (first ns-map)) 
                        cmplr)
             exe    (.compile cmplr xquery)
             evaluator (.load exe)]

        (fn [#^XdmNode xml] 
            ; TODO add variable support
            ;(.setExternalVariable #^Qname name #^XdmValue val)
            (.setContextItem evaluator xml)
            (unwrap-xdm-items evaluator))))

;; Serializing

(defn- write-value
  [#^XdmValue node #^Destination serializer]
  (.writeXdmValue (get-proc) node serializer))

(defmulti serialize (fn [node dest] (class dest)))
  (defmethod serialize java.io.File
    [node #^java.io.File dest]
    (let [s (Serializer.)]
      (write-value node (doto s (.setOutputFile dest)))))
  (defmethod serialize java.io.OutputStream
    [node #^java.io.OutputStream dest]
    (let [s (Serializer.)]
      (write-value node (doto s (.setOutputStream dest)))))
  (defmethod serialize java.io.Writer
    [node #^java.io.Writer dest]
    (let [s (Serializer.)]
      (write-value node (doto s (.setOutputWriter dest)))))

;; Node functions

(defn parent-node
    "Returns parent node of passed node."
    [#^XdmNode nd]
    (.getParent nd))

(defn node-name
    "Returns the name of the node (as QName)."
    [#^XdmNode nd]
    (.getNodeName nd))

(defn node-ns
    "Returns the namespace of the node or node name."
    [q]
    (if (= (class q) QName)
        (.getNamespaceURI #^QName q)
        (node-ns (node-name q))))

(def #^{:private true} 
    node-kind-map
        {XdmNodeKind/DOCUMENT   :document
         XdmNodeKind/ELEMENT    :element
         XdmNodeKind/ATTRIBUTE  :attribute
         XdmNodeKind/TEXT       :text
         XdmNodeKind/COMMENT    :comment
         XdmNodeKind/NAMESPACE  :namespace
         XdmNodeKind/PROCESSING_INSTRUCTION :processing-instruction})

(defn node-kind
    "Returns keyword corresponding to node's kind."
    [#^XdmNode nd]
    (node-kind-map (.getNodeKind nd)))

(defn node-path
    "Returns XPath to node."
    [#^XdmNode nd]
    (Navigator/getPath (.getUnderlyingNode nd)))

;(def #^{:private true} 
;    axis-map
;        {:ancestor            Axis/ANCESTOR           
;         :ancestor-or-self    Axis/ANCESTOR_OR_SELF   
;         :attribute           Axis/ATTRIBUTE          
;         :child               Axis/CHILD              
;         :descendant          Axis/DESCENDANT         
;         :descendant-or-self  Axis/DESCENDANT_OR_SELF 
;         :following           Axis/FOLLOWING          
;         :following-sibling   Axis/FOLLOWING_SIBLING  
;         :parent              Axis/PARENT             
;         :preceding           Axis/PRECEDING          
;         :preceding-sibling   Axis/PRECEDING_SIBLING  
;         :self                Axis/SELF               
;         :namespace           Axis/NAMESPACE})
;
;(defn axis-seq
;   "Returns sequences of nodes on given axis."
;   ([#^XdmNode nd axis]
;    (.axisIterator nd #^Axis (axis-map axis)))
;   ([#^XdmNode nd axis name]
;    (.axisIterator nd #^Axis (axis-map axis) (QName. #^String name))))

; Node-kind predicates

(defn document?
    "Returns true if node is document."
    [#^XdmNode nd]
    (.equals (.getNodeKind nd) XdmNodeKind/DOCUMENT))

(defn element?
    "Returns true if node is element."
    [#^XdmNode nd]
    (.equals (.getNodeKind nd) XdmNodeKind/ELEMENT))

(defn attribute?
    "Returns true if node is attribute."
    [#^XdmNode nd]
    (.equals (.getNodeKind nd) XdmNodeKind/ATTRIBUTE))

(defn text?
    "Returns true if node is text."
    [#^XdmNode nd]
    (.equals (.getNodeKind nd) XdmNodeKind/TEXT))

(defn comment?
    "Returns true if node is comment."
    [#^XdmNode nd]
    (.equals (.getNodeKind nd) XdmNodeKind/COMMENT))
  
(defn namespace?
    "Returns true if node is namespace."
    [#^XdmNode nd]
    (.equals (.getNodeKind nd) XdmNodeKind/NAMESPACE))

(defn processing-instruction?
    "Returns true if node is processing instruction."
    [#^XdmNode nd]
    (.equals (.getNodeKind nd) XdmNodeKind/PROCESSING_INSTRUCTION))

