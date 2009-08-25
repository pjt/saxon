Saxon
=====

Simple Clojure wrapper for Michael Kay's [Saxon XSLT and XQuery Processor][saxonica] (from
Saxonica Limited). 

Dependencies
------------

Are included in this distribution in the /deps directory. When built with Ant, are included
in the resulting saxon.jar.

Use
---

You can compile XML strings or files into nodes in memory ("XdmNodes"), as in

    user=> (use 'saxon)
    nil
    user=> (def node1 (compile-string "<doc><word>Hi</word><punc>!</punc></doc>"))
    #'user/node1
    user=> node1
    #<XdmNode <doc>
       <word>Hi</word>
       <punc>!</punc>
    </doc>>
    user=> (println (str node1))
    <doc>
       <word>Hi</word>
       <punc>!</punc>
    </doc>
    nil

`compile-file` reads an XML document from the filesystem into a document node.

To process nodes, use the functions returned from `compile-xslt`, `compile-xpath`,
& `compile-xquery`. `compile-xpath` & `compile-xquery` return a lazy sequence of 
matching nodes (the evaluation is done lazily in Saxon, as well).

    user=> (import 'java.io.File)
    user=> (compile-xslt (File. "strip.xsl"))
    #<saxon$compile_xslt__53$fn__55 saxon$compile_xslt__53$fn__55@51d098b7>
    user=> ((compile-xslt (File. "strip.xsl")) node1)
    #<XdmNode Hi!>
    ; strip removes everything but text nodes
    user=> (def strip (compile-xslt (File. "strip.xsl")))
    #'user/strip
    user=> (strip node1)
    #<XdmNode Hi!>

    user=> ((compile-xpath "//punc") node1)
    #<XdmNode <punc>!</punc>>
    user=> ((compile-xpath "//punc/string()") node1)
    "!"
    user=> (def punc-str (compile-xpath "//punc/string()"))
    #'user/punc-str
    user=> (punc-str node1)
    "!"


In addition to the compilation & processing functions, there are some helper
functions like `parent-node`, `node-kind`, etc., as well as node-kind predicates
like `document?`, `element?`, etc. There is also `node-path`, which returns the
XPath to the passed node:

    user=> (node-path ((compile-xpath "//punc") node1))
    "/doc/punc[1]"
 


   [saxonica]: http://saxonica.com/
   [sfdl]: http://sourceforge.net/project/showfiles.php?group_id=29872&package_id=21888
