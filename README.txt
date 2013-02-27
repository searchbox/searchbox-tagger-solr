Welcome to Searchbox-Tagger!
-----------------------------------

This document explains how the Searchbox-Tagger plugin 
can be configured to provide a Solr search component to add 
out-of-the-box high quality tags to documents. In addition, this
search component can be used to produce tags given plain text as a
query, obviating the need to create a document to get tags.

The Searchbox-Tagger plugin analyses the designated full-text fields
and creates a tagging model which holds a list of possible tags with
their relative importance. When a query is submitted, the search component 
quickly (< 80ms per document) identifies phrases which are important
for the text and returns them in the Solr response. We also allow for
a user specified boost file, which boosts the associated keywords by
the user specified amount.

We recommend you head over to our website and register your email 
address to receive notifications about software updates and 
new products. We're rolling out additional tools for different 
domains in the coming months, so stay tuned!

Getting Started
---------------
Add the following request handler to the appropriate solrconfig.xml:


		
	 <searchComponent class="com.searchbox.TaggerComponent" name="sbtagger">
        <lst name="queryFields">
            <str name="queryField">article-title</str>
			<str name="queryField">article-abstract</str>
        </lst>
        <str name="buildOnCommit">true</str>
        <str name="buildOnOptimize">true</str>
        <str name="storeDir">sbtagger</str>
		<int name="maxNumDocs">50000</int>
		<str name="boostsfile">/salsasvn/boostsfile.txt</str>
		<str name="key">YOUR_PRODUCT_KEY</str>
	</searchComponent>
	
We will explain the meaning and usage of the various parameters:


	<lst name="fields">
		<str name="field">article-title</str>
		<str name="field">article-abstract</str>
	</lst>

We need to define the schema fields which will be used for analysis of the corpus.  
Analysis of these fields requires that they are Stored=true so that 
the raw full text is available. The list can contain one or more fields.
	
	<str name="buildOnCommit">true</str>
	<str name="buildOnOptimize">true</str>

These options define if the tagger model should be (re)build upon a solr commit or
upon optimize. This works very similar to the Solr spellchecker plugin and
thus their comments hold: Building on commit is expensive and is discouraged for most 
production systems.  For large indexes, one commit may take minutes since the building of 
tagger model is single threaded. Typically one uses buildOnOptimize or explicit build instead.

	<str name="storeDir">sbtagger</str>

This is the directory where the serialied model will be stored. It can be either an aboslute
directory (starting with "/") or a relative directory to Solr's data directory. If the
path doesn't exist, the necessary directories are created so it is valid.

	<int name="maxNumDocs">50000</int>

The maximum number of doucments to analyze to produce the tagger model. If set to -1, then
all documents in the repository are used. The higher the number the more resources and time
are required to compute and store. 
	
	<int name="minDocFreq">2</int>
	<int name="minTermFreq">2</int>

These two parameters put a limit on the phrases which are considered acceptable. In this
case we specify that a tag must appear in at least 2 documents and must appear in total
at least twice. The higher the number, the less tags will be modeled and thus require
notably less processing time and resources. Considering that the taggerion is a probablistic
model, if it is known that there are many phrases which appear very infrequently and
don't have a high recall value, putting these numbers higher will result in gained performance.
The default is 2 for each and gives very well represented results.


	<str name="boostsfile">/salsasvn/boostsfile.txt</str>

The plugin supports user specified tag boost files to artifically increase or decrease
the scores of certain tags. The format of the file is keyword TAB percent_boost. If 
percent_boost is equal to 1.0 then no artifical boosting takes place, a score of 2.0 
forces the score to be twice as high as it would normally be, respectively if the score 
is 0.5 then the score is half of what it would normally be.
	
	<str name="key">YOUR_PRODUCT_KEY</str>

Of course you'll need to replace YOUR_PRODUCT_KEY from the product key you obtained
at www.searchbox.com or by emailing contact@searchbox.com

			
Usage Of Search Component
---------------

Although search components are intended to be used in line with searchers, it is 
possible to define a request handler for example purposes (similar to the default
installation of Solr and their demonstration of the spell checker). To do this
simply add to solrconfig.xml:

	<requestHandler name="/sbtagger" class="solr.SearchHandler">
		<arr name="last-components">
			<str>sbtagger</str>
		</arr>
	</requestHandler>


The following are acceptable URL (and thus configuration) options which
can be set:

	sbtagger.build=true

THIS IS REQUIRED upon first running to create the model, especially if buildOnOptimize
and buildOnCommit are not set to true. If this option isn't sent with the first
request there will be no model to process the query. This also throws an error
in the Solr Log. Given the size of the repository and processor power of the machines
this can take from a few seconds to a few minutes.

	sbtagger.count

Defines the number of possible tags to return. The default is 5    
	
	sbtagger.q

Different from the Solr spell checker, this parameter DOES NOT override the common q parameter. 
It is used only in the case where the common q parameters (q="...") is empty. It can be used
to tag a free piece of text which isn't a document. For a more full explanation see
the following section.
	

Understanding the results
----------------------------

A sample response using the tagger to tag free text is presented below:

http://192.168.56.101:8982/pubmed_demo/sbtag?q=&sbtagger.q=BackgroundThe%20functional%20sites%20of%20a%20protein%20present%20important%20information%20for%20determining%20its%20cellular%20function%20and%20are%20fundamental%20in%20drug%20design.%20Accordingly,%20accurate%20methods%20for%20the%20prediction%20of%20functional%20sites%20are%20of%20immense%20value.%20Most%20available%20methods%20are%20based%20on%20a%20set%20of%20homologous%20sequences%20and%20structural%20or%20evolutionary%20information,%20and%20assume%20that%20functional%20sites%20are%20more%20conserved%20than%20the%20average.%20In%20the%20analysis%20presented%20here,%20we%20have%20investigated%20the%20conservation%20of%20location%20and%20type%20of%20amino%20acids%20at%20functional%20sites,%20and%20compared%20the%20behaviour%20of%20functional%20sites%20between%20different%20protein%20domains.ResultsFunctional%20sites%20were%20extracted%20from%20experimentally%20determined%20structural%20complexes%20from%20the%20Protein%20Data%20Bank%20harbouring%20a%20conserved%20protein%20domain%20from%20the%20SMART%20database.%20In%20general,%20functional%20(i.e.%20interacting)%20sites%20whose%20location%20is%20more%20highly%20conserved%20are%20also%20more%20conserved%20in%20their%20type%20of%20amino%20acid.%20However,%20even%20highly%20conserved%20functional%20sites%20can%20present%20a%20wide%20spectrum%20of%20amino%20acids.%20The%20degree%20of%20conservation%20strongly%20depends%20on%20the%20function%20of%20the%20protein%20domain%20and%20ranges%20from%20highly%20conserved%20in%20location%20and%20amino%20acid%20to%20very%20variable.%20Differentiation%20by%20binding%20partner%20shows%20that%20ion%20binding%20sites%20tend%20to%20be%20more%20conserved%20than%20functional%20sites%20binding%20peptides%20or%20nucleotides.ConclusionThe%20results%20gained%20by%20this%20analysis%20will%20help%20improve%20the%20accuracy%20of%20functional%20site%20prediction%20and%20facilitate%20the%20characterization%20of%20unknown%20protein%20sequences.

	<response>
		<lst name="responseHeader">
			<int name="status">0</int>
			<int name="QTime">668</int>
		</lst>
		<result name="response" numFound="0" start="0"/>
		<lst name="sbtagger">
			<lst name="sbtagger.q">
				<double name="functional sites binding peptides ">23.459341475042095</double>
				<double name="backgroundthe functional sites ">19.063731073787036</double>
				<double name="functional sites ">17.734912434901865</double>
				<double name="protein domains.resultsfunctional sites ">15.68703080866825</double>
				<double name="binding sites ">15.566828349168397</double>
			</lst>
		</lst>
	</response>


A sample response using the tagger as a search component is presented below:

http://192.168.56.101:8982/pubmed_demo/sbtag?q=*%3A*&wt=xml	

<response>
    <lst name="responseHeader">
        <int name="status">0</int>
        <int name="QTime">779</int>
    </lst>
    <result name="response" numFound="13262" start="0">
        <doc>
            <str name="id">f73ca075-3826-45d5-85df-64b33c760efc</str>
            <str name="article-title">Variation in structural location and amino acid conservation of functional sites in protein domain families</str>
            <arr name="content">
                <str>Variation in structural location and amino acid conservation of functional sites in protein domain families</str>
                <str>BackgroundThe functional sites of a protein present important information for determining its cellular function and are fundamental in drug design. Accordingly, accurate methods for the prediction of functional sites are of immense value. Most available methods are based on a set of homologous sequences and structural or evolutionary information, and assume that functional sites are more conserved than the average. In the analysis presented here, we have investigated the conservation of location and type of amino acids at functional sites, and compared the behaviour of functional sites between different protein domains.ResultsFunctional sites were extracted from experimentally determined structural complexes from the Protein Data Bank harbouring a conserved protein domain from the SMART database. In general, functional (i.e. interacting) sites whose location is more highly conserved are also more conserved in their type of amino acid. However, even highly conserved functional sites can present a wide spectrum of amino acids. The degree of conservation strongly depends on the function of the protein domain and ranges from highly conserved in location and amino acid to very variable. Differentiation by binding partner shows that ion binding sites tend to be more conserved than functional sites binding peptides or nucleotides.ConclusionThe results gained by this analysis will help improve the accuracy of functional site prediction and facilitate the characterization of unknown protein sequences.</str>
            </arr>
            <str name="file">BMC_Bioinformatics_2005_Aug_25_6_210.nxml</str>
            <arr name="article-abstract">
                <str>BackgroundThe functional sites of a protein present important information for determining its cellular function and are fundamental in drug design. Accordingly, accurate methods for the prediction of functional sites are of immense value. Most available methods are based on a set of homologous sequences and structural or evolutionary information, and assume that functional sites are more conserved than the average. In the analysis presented here, we have investigated the conservation of location and type of amino acids at functional sites, and compared the behaviour of functional sites between different protein domains.ResultsFunctional sites were extracted from experimentally determined structural complexes from the Protein Data Bank harbouring a conserved protein domain from the SMART database. In general, functional (i.e. interacting) sites whose location is more highly conserved are also more conserved in their type of amino acid. However, even highly conserved functional sites can present a wide spectrum of amino acids. The degree of conservation strongly depends on the function of the protein domain and ranges from highly conserved in location and amino acid to very variable. Differentiation by binding partner shows that ion binding sites tend to be more conserved than functional sites binding peptides or nucleotides.ConclusionThe results gained by this analysis will help improve the accuracy of functional site prediction and facilitate the characterization of unknown protein sequences.</str>
            </arr>
            <str name="_version_">1421611432350318592</str>
        </doc>
        <doc>
            <str name="id">bc72dbef-87d1-4c39-b388-ec67babe6f05</str>
            <str name="article-title">Mining metastasis related genes by primary-secondary tumor comparisons from large-scale databases</str>
            <arr name="content">
                <str>Mining metastasis related genes by primary-secondary tumor comparisons from large-scale databases</str>
                <str>BackgroundMetastasis is the most dangerous step in cancer progression and causes more than 90% of cancer death. Although many researchers have been working on biological features and characteristics of metastasis, most of its genetic level processes remain uncertain. Some studies succeeded in elucidating metastasis related genes and pathways, followed by predicting prognosis of cancer patients, but there still is a question whether the result genes or pathways contain enough information and noise features have been controlled appropriately.MethodsWe set four tumor type classes composed of various tumor characteristics such as tissue origin, cellular environment, and metastatic ability. We conducted a set of comparisons among the four tumor classes followed by searching for genes that are consistently up or down regulated through the whole comparisons.ResultsWe identified four sets of genes that are consistently differently expressed in the comparisons, each of which denotes one of four cellular characteristics respectively – liver tissue, colon tissue, liver viability and metastasis characteristics. We found that our candidate genes for tissue specificity are consistent with the TiGER database. And we also found that the metastasis candidate genes from our method were more consistent with the known biological background and independent from other noise features.ConclusionWe suggested a new method for identifying metastasis related genes from a large-scale database. The proposed method attempts to minimize the influences from other factors except metastatic ability including tissue originality and tissue viability by confining the result of metastasis unrelated test combinations.</str>
            </arr>
            <str name="file">BMC_Bioinformatics_2009_Mar_19_10(Suppl_3)_S2.nxml</str>
            <arr name="article-abstract">
                <str>BackgroundMetastasis is the most dangerous step in cancer progression and causes more than 90% of cancer death. Although many researchers have been working on biological features and characteristics of metastasis, most of its genetic level processes remain uncertain. Some studies succeeded in elucidating metastasis related genes and pathways, followed by predicting prognosis of cancer patients, but there still is a question whether the result genes or pathways contain enough information and noise features have been controlled appropriately.MethodsWe set four tumor type classes composed of various tumor characteristics such as tissue origin, cellular environment, and metastatic ability. We conducted a set of comparisons among the four tumor classes followed by searching for genes that are consistently up or down regulated through the whole comparisons.ResultsWe identified four sets of genes that are consistently differently expressed in the comparisons, each of which denotes one of four cellular characteristics respectively – liver tissue, colon tissue, liver viability and metastasis characteristics. We found that our candidate genes for tissue specificity are consistent with the TiGER database. And we also found that the metastasis candidate genes from our method were more consistent with the known biological background and independent from other noise features.ConclusionWe suggested a new method for identifying metastasis related genes from a large-scale database. The proposed method attempts to minimize the influences from other factors except metastatic ability including tissue originality and tissue viability by confining the result of metastasis unrelated test combinations.</str>
            </arr>
            <str name="_version_">1421611432353464320</str>
        </doc>
        <doc>
            <str name="id">bfdb855d-465c-479e-94ef-9567b178788d</str>
            <str name="article-title">Detecting pore-lining regions in transmembrane protein sequences</str>
            <arr name="content">
                <str>Detecting pore-lining regions in transmembrane protein sequences</str>
                <str>BackgroundAlpha-helical transmembrane channel and transporter proteins play vital roles in a diverse range of essential biological processes and are crucial in facilitating the passage of ions and molecules across the lipid bilayer. However, the experimental difficulties associated with obtaining high quality crystals has led to their significant under-representation in structural databases. Computational methods that can identify structural features from sequence alone are therefore of high importance.ResultsWe present a method capable of automatically identifying pore-lining regions in transmembrane proteins from sequence information alone, which can then be used to determine the pore stoichiometry. By labelling pore-lining residues in crystal structures using geometric criteria, we have trained a support vector machine classifier to predict the likelihood of a transmembrane helix being involved in pore formation. Results from testing this approach under stringent cross-validation indicate that prediction accuracy of 72% is possible, while a support vector regression model is able to predict the number of subunits participating in the pore with 62% accuracy.ConclusionTo our knowledge, this is the first tool capable of identifying pore-lining regions in proteins and we present the results of applying it to a data set of sequences with available crystal structures. Our method provides a way to characterise pores in transmembrane proteins and may even provide a starting point for discovering novel routes of therapeutic intervention in a number of important diseases. This software is freely available as source code from: http://bioinf.cs.ucl.ac.uk/downloads/memsat-svm/.</str>
            </arr>
            <str name="file">BMC_Bioinformatics_2012_Jul_17_13_169.nxml</str>
            <arr name="article-abstract">
                <str>BackgroundAlpha-helical transmembrane channel and transporter proteins play vital roles in a diverse range of essential biological processes and are crucial in facilitating the passage of ions and molecules across the lipid bilayer. However, the experimental difficulties associated with obtaining high quality crystals has led to their significant under-representation in structural databases. Computational methods that can identify structural features from sequence alone are therefore of high importance.ResultsWe present a method capable of automatically identifying pore-lining regions in transmembrane proteins from sequence information alone, which can then be used to determine the pore stoichiometry. By labelling pore-lining residues in crystal structures using geometric criteria, we have trained a support vector machine classifier to predict the likelihood of a transmembrane helix being involved in pore formation. Results from testing this approach under stringent cross-validation indicate that prediction accuracy of 72% is possible, while a support vector regression model is able to predict the number of subunits participating in the pore with 62% accuracy.ConclusionTo our knowledge, this is the first tool capable of identifying pore-lining regions in proteins and we present the results of applying it to a data set of sequences with available crystal structures. Our method provides a way to characterise pores in transmembrane proteins and may even provide a starting point for discovering novel routes of therapeutic intervention in a number of important diseases. This software is freely available as source code from: http://bioinf.cs.ucl.ac.uk/downloads/memsat-svm/.</str>
            </arr>
            <str name="_version_">1421611432353464321</str>
        </doc>
        <doc>
            <str name="id">724e4696-2f5e-47f0-b782-d28feeb64d56</str>
            <str name="article-title">GO-Diff: Mining functional differentiation between EST-based transcriptomes</str>
            <arr name="content">
                <str>GO-Diff: Mining functional differentiation between EST-based transcriptomes</str>
                <str>BackgroundLarge-scale sequencing efforts produced millions of Expressed Sequence Tags (ESTs) collectively representing differentiated biochemical and functional states. Analysis of these EST libraries reveals differential gene expressions, and therefore EST data sets constitute valuable resources for comparative transcriptomics. To translate differentially expressed genes into a better understanding of the underlying biological phenomena, existing microarray analysis approaches usually involve the integration of gene expression with Gene Ontology (GO) databases to derive comparable functional profiles. However, methods are not available yet to process EST-derived transcription maps to enable GO-based global functional profiling for comparative transcriptomics in a high throughput manner.ResultsHere we present GO-Diff, a GO-based functional profiling approach towards high throughput EST-based gene expression analysis and comparative transcriptomics. Utilizing holistic gene expression information, the software converts EST frequencies into EST Coverage Ratios of GO Terms. The ratios are then tested for statistical significances to uncover differentially represented GO terms between the compared transcriptomes, and functional differences are thus inferred. We demonstrated the validity and the utility of this software by identifying differentially represented GO terms in three application cases: intra-species comparison; meta-analysis to test a specific hypothesis; inter-species comparison. GO-Diff findings were consistent with previous knowledge and provided new clues for further discoveries. A comprehensive test on the GO-Diff results using series of comparisons between EST libraries of human and mouse tissues showed acceptable levels of consistency: 61% for human-human; 69% for mouse-mouse; 47% for human-mouse.ConclusionGO-Diff is the first software integrating EST profiles with GO knowledge databases to mine functional differentiation between biological systems, e.g. tissues of the same species or the same tissue cross species. With rapid accumulation of EST resources in the public domain and expanding sequencing effort in individual laboratories, GO-Diff is useful as a screening tool before undertaking serious expression studies.</str>
            </arr>
            <str name="file">BMC_Bioinformatics_2006_Feb_16_7_72.nxml</str>
            <arr name="article-abstract">
                <str>BackgroundLarge-scale sequencing efforts produced millions of Expressed Sequence Tags (ESTs) collectively representing differentiated biochemical and functional states. Analysis of these EST libraries reveals differential gene expressions, and therefore EST data sets constitute valuable resources for comparative transcriptomics. To translate differentially expressed genes into a better understanding of the underlying biological phenomena, existing microarray analysis approaches usually involve the integration of gene expression with Gene Ontology (GO) databases to derive comparable functional profiles. However, methods are not available yet to process EST-derived transcription maps to enable GO-based global functional profiling for comparative transcriptomics in a high throughput manner.ResultsHere we present GO-Diff, a GO-based functional profiling approach towards high throughput EST-based gene expression analysis and comparative transcriptomics. Utilizing holistic gene expression information, the software converts EST frequencies into EST Coverage Ratios of GO Terms. The ratios are then tested for statistical significances to uncover differentially represented GO terms between the compared transcriptomes, and functional differences are thus inferred. We demonstrated the validity and the utility of this software by identifying differentially represented GO terms in three application cases: intra-species comparison; meta-analysis to test a specific hypothesis; inter-species comparison. GO-Diff findings were consistent with previous knowledge and provided new clues for further discoveries. A comprehensive test on the GO-Diff results using series of comparisons between EST libraries of human and mouse tissues showed acceptable levels of consistency: 61% for human-human; 69% for mouse-mouse; 47% for human-mouse.ConclusionGO-Diff is the first software integrating EST profiles with GO knowledge databases to mine functional differentiation between biological systems, e.g. tissues of the same species or the same tissue cross species. With rapid accumulation of EST resources in the public domain and expanding sequencing effort in individual laboratories, GO-Diff is useful as a screening tool before undertaking serious expression studies.</str>
            </arr>
            <str name="_version_">1421611432354512896</str>
        </doc>
        <doc>
            <str name="id">0d133582-62f0-4f30-9643-7f83705fe2c5</str>
            <str name="article-title">The PathOlogist: an automated tool for pathway-centric analysis</str>
            <arr name="content">
                <str>The PathOlogist: an automated tool for pathway-centric analysis</str>
                <str>BackgroundThe PathOlogist is a new tool designed to transform large sets of gene expression data into quantitative descriptors of pathway-level behavior. The tool aims to provide a robust alternative to the search for single-gene-to-phenotype associations by accounting for the complexity of molecular interactions.ResultsMolecular abundance data is used to calculate two metrics - 'activity' and 'consistency' - for each pathway in a set of more than 500 canonical molecular pathways (source: Pathway Interaction Database, http://pid.nci.nih.gov). The tool then allows a detailed exploration of these metrics through integrated visualization of pathway components and structure, hierarchical clustering of pathways and samples, and statistical analyses designed to detect associations between pathway behavior and clinical features.ConclusionsThe PathOlogist provides a straightforward means to identify the functional processes, rather than individual molecules, that are altered in disease. The statistical power and biologic significance of this approach are made easily accessible to laboratory researchers and informatics analysts alike. Here we show as an example, how the PathOlogist can be used to establish pathway signatures that robustly differentiate breast cancer cell lines based on response to treatment.</str>
            </arr>
            <str name="file">BMC_Bioinformatics_2011_May_4_12_133.nxml</str>
            <arr name="article-abstract">
                <str>BackgroundThe PathOlogist is a new tool designed to transform large sets of gene expression data into quantitative descriptors of pathway-level behavior. The tool aims to provide a robust alternative to the search for single-gene-to-phenotype associations by accounting for the complexity of molecular interactions.ResultsMolecular abundance data is used to calculate two metrics - 'activity' and 'consistency' - for each pathway in a set of more than 500 canonical molecular pathways (source: Pathway Interaction Database, http://pid.nci.nih.gov). The tool then allows a detailed exploration of these metrics through integrated visualization of pathway components and structure, hierarchical clustering of pathways and samples, and statistical analyses designed to detect associations between pathway behavior and clinical features.ConclusionsThe PathOlogist provides a straightforward means to identify the functional processes, rather than individual molecules, that are altered in disease. The statistical power and biologic significance of this approach are made easily accessible to laboratory researchers and informatics analysts alike. Here we show as an example, how the PathOlogist can be used to establish pathway signatures that robustly differentiate breast cancer cell lines based on response to treatment.</str>
            </arr>
            <str name="_version_">1421611432355561472</str>
        </doc>
        <doc>
            <str name="id">4c2944b7-dc82-422e-ba9b-ea48fbf402b3</str>
            <str name="article-title">Publishing perishing? Towards tomorrow's information architecture</str>
            <arr name="content">
                <str>Publishing perishing? Towards tomorrow's information architecture</str>
                <str>Scientific articles are tailored to present information in human-readable aliquots. Although the Internet has revolutionized the way our society thinks about information, the traditional text-based framework of the scientific article remains largely unchanged. This format imposes sharp constraints upon the type and quantity of biological information published today. Academic journals alone cannot capture the findings of modern genome-scale inquiry.Like many other disciplines, molecular biology is a science of facts: information inherently suited to database storage. In the past decade, a proliferation of public and private databases has emerged to house genome sequence, protein structure information, functional genomics data and more; these digital repositories are now a vital component of scientific communication. The next challenge is to integrate this vast and ever-growing body of information with academic journals and other media. To truly integrate scientific information we must modernize academic publishing to exploit the power of the Internet. This means more than online access to articles, hyperlinked references and web-based supplemental data; it means making articles fully computer-readable with intelligent markup and Structured Digital Abstracts.Here, we examine the changing roles of scholarly journals and databases. We present our vision of the optimal information architecture for the biosciences, and close with tangible steps to improve our handling of scientific information today while paving the way for an expansive central index in the future.</str>
            </arr>
            <str name="file">BMC_Bioinformatics_2007_Jan_19_8_17.nxml</str>
            <arr name="article-abstract">
                <str>Scientific articles are tailored to present information in human-readable aliquots. Although the Internet has revolutionized the way our society thinks about information, the traditional text-based framework of the scientific article remains largely unchanged. This format imposes sharp constraints upon the type and quantity of biological information published today. Academic journals alone cannot capture the findings of modern genome-scale inquiry.Like many other disciplines, molecular biology is a science of facts: information inherently suited to database storage. In the past decade, a proliferation of public and private databases has emerged to house genome sequence, protein structure information, functional genomics data and more; these digital repositories are now a vital component of scientific communication. The next challenge is to integrate this vast and ever-growing body of information with academic journals and other media. To truly integrate scientific information we must modernize academic publishing to exploit the power of the Internet. This means more than online access to articles, hyperlinked references and web-based supplemental data; it means making articles fully computer-readable with intelligent markup and Structured Digital Abstracts.Here, we examine the changing roles of scholarly journals and databases. We present our vision of the optimal information architecture for the biosciences, and close with tangible steps to improve our handling of scientific information today while paving the way for an expansive central index in the future.</str>
            </arr>
            <str name="_version_">1421611432356610048</str>
        </doc>
        <doc>
            <str name="id">f3a8183c-d074-4220-8a54-74a79fdfac62</str>
            <str name="article-title">Application of a sensitive collection heuristic for very large protein families: Evolutionary relationship between adipose triglyceride lipase (ATGL) and classic mammalian lipases</str>
            <arr name="content">
                <str>Application of a sensitive collection heuristic for very large protein families: Evolutionary relationship between adipose triglyceride lipase (ATGL) and classic mammalian lipases</str>
                <str>BackgroundManually finding subtle yet statistically significant links to distantly related homologues becomes practically impossible for very populated protein families due to the sheer number of similarity searches to be invoked and analyzed. The unclear evolutionary relationship between classical mammalian lipases and the recently discovered human adipose triglyceride lipase (ATGL; a patatin family member) is an exemplary case for such a problem.ResultsWe describe an unsupervised, sensitive sequence segment collection heuristic suitable for assembling very large protein families. It is based on fan-like expanding, iterative database searches. To prevent inclusion of unrelated hits, additional criteria are introduced: minimal alignment length and overlap with starting sequence segments, finding starting sequences in reciprocal searches, automated filtering for compositional bias and repetitive patterns. This heuristic was implemented as FAMILYSEARCHER in the ANNIE sequence analysis environment and applied to search for protein links between the classical lipase family and the patatin-like group.ConclusionThe FAMILYSEARCHER is an efficient tool for tracing distant evolutionary relationships involving large protein families. Although classical lipases and ATGL have no obvious sequence similarity and differ with regard to fold and catalytic mechanism, homology links detected with FAMILYSEARCHER show that they are evolutionarily related. The conserved sequence parts can be narrowed down to an ancestral core module consisting of three ß-strands, one a-helix and a turn containing the typical nucleophilic serine. Moreover, this ancestral module also appears in numerous enzymes with various substrate specificities, but that critically rely on nucleophilic attack mechanisms.</str>
            </arr>
            <str name="file">BMC_Bioinformatics_2006_Mar_21_7_164.nxml</str>
            <arr name="article-abstract">
                <str>BackgroundManually finding subtle yet statistically significant links to distantly related homologues becomes practically impossible for very populated protein families due to the sheer number of similarity searches to be invoked and analyzed. The unclear evolutionary relationship between classical mammalian lipases and the recently discovered human adipose triglyceride lipase (ATGL; a patatin family member) is an exemplary case for such a problem.ResultsWe describe an unsupervised, sensitive sequence segment collection heuristic suitable for assembling very large protein families. It is based on fan-like expanding, iterative database searches. To prevent inclusion of unrelated hits, additional criteria are introduced: minimal alignment length and overlap with starting sequence segments, finding starting sequences in reciprocal searches, automated filtering for compositional bias and repetitive patterns. This heuristic was implemented as FAMILYSEARCHER in the ANNIE sequence analysis environment and applied to search for protein links between the classical lipase family and the patatin-like group.ConclusionThe FAMILYSEARCHER is an efficient tool for tracing distant evolutionary relationships involving large protein families. Although classical lipases and ATGL have no obvious sequence similarity and differ with regard to fold and catalytic mechanism, homology links detected with FAMILYSEARCHER show that they are evolutionarily related. The conserved sequence parts can be narrowed down to an ancestral core module consisting of three ß-strands, one a-helix and a turn containing the typical nucleophilic serine. Moreover, this ancestral module also appears in numerous enzymes with various substrate specificities, but that critically rely on nucleophilic attack mechanisms.</str>
            </arr>
            <str name="_version_">1421611432357658624</str>
        </doc>
        <doc>
            <str name="id">9a6f4fb4-2836-42a7-be77-34418d2aea55</str>
            <str name="article-title">In silico analysis of expressed sequence tags from Trichostrongylus vitrinus (Nematoda): comparison of the automated ESTExplorer workflow platform with conventional database searches</str>
            <arr name="content">
                <str>In silico analysis of expressed sequence tags from Trichostrongylus vitrinus (Nematoda): comparison of the automated ESTExplorer workflow platform with conventional database searches</str>
                <str>BackgroundThe analysis of expressed sequence tags (EST) offers a rapid and cost effective approach to elucidate the transcriptome of an organism, but requires several computational methods for assembly and annotation. Researchers frequently analyse each step manually, which is laborious and time consuming. We have recently developed ESTExplorer, a semi-automated computational workflow system, in order to achieve the rapid analysis of EST datasets. In this study, we evaluated EST data analysis for the parasitic nematode Trichostrongylus vitrinus (order Strongylida) using ESTExplorer, compared with database matching alone.ResultsWe functionally annotated 1776 ESTs obtained via suppressive-subtractive hybridisation from T. vitrinus, an important parasitic trichostrongylid of small ruminants. Cluster and comparative genomic analyses of the transcripts using ESTExplorer indicated that 290 (41%) sequences had homologues in Caenorhabditis elegans, 329 (42%) in parasitic nematodes, 202 (28%) in organisms other than nematodes, and 218 (31%) had no significant match to any sequence in the current databases. Of the C. elegans homologues, 90 were associated with 'non-wildtype' double-stranded RNA interference (RNAi) phenotypes, including embryonic lethality, maternal sterility, sterile progeny, larval arrest and slow growth. We could functionally classify 267 (38%) sequences using the Gene Ontologies (GO) and establish pathway associations for 230 (33%) sequences using the Kyoto Encyclopedia of Genes and Genomes (KEGG). Further examination of this EST dataset revealed a number of signalling molecules, proteases, protease inhibitors, enzymes, ion channels and immune-related genes. In addition, we identified 40 putative secreted proteins that could represent potential candidates for developing novel anthelmintics or vaccines. We further compared the automated EST sequence annotations, using ESTExplorer, with database search results for individual T. vitrinus ESTs. ESTExplorer reliably and rapidly annotated 301 ESTs, with pathway and GO information, eliminating 60 low quality hits from database searches.ConclusionWe evaluated the efficacy of ESTExplorer in analysing EST data, and demonstrate that computational tools can be used to accelerate the process of gene discovery in EST sequencing projects. The present study has elucidated sets of relatively conserved and potentially novel genes for biological investigation, and the annotated EST set provides further insight into the molecular biology of T. vitrinus, towards the identification of novel drug targets.</str>
            </arr>
            <str name="file">BMC_Bioinformatics_2008_Feb_13_9(Suppl_1)_S10.nxml</str>
            <arr name="article-abstract">
                <str>BackgroundThe analysis of expressed sequence tags (EST) offers a rapid and cost effective approach to elucidate the transcriptome of an organism, but requires several computational methods for assembly and annotation. Researchers frequently analyse each step manually, which is laborious and time consuming. We have recently developed ESTExplorer, a semi-automated computational workflow system, in order to achieve the rapid analysis of EST datasets. In this study, we evaluated EST data analysis for the parasitic nematode Trichostrongylus vitrinus (order Strongylida) using ESTExplorer, compared with database matching alone.ResultsWe functionally annotated 1776 ESTs obtained via suppressive-subtractive hybridisation from T. vitrinus, an important parasitic trichostrongylid of small ruminants. Cluster and comparative genomic analyses of the transcripts using ESTExplorer indicated that 290 (41%) sequences had homologues in Caenorhabditis elegans, 329 (42%) in parasitic nematodes, 202 (28%) in organisms other than nematodes, and 218 (31%) had no significant match to any sequence in the current databases. Of the C. elegans homologues, 90 were associated with 'non-wildtype' double-stranded RNA interference (RNAi) phenotypes, including embryonic lethality, maternal sterility, sterile progeny, larval arrest and slow growth. We could functionally classify 267 (38%) sequences using the Gene Ontologies (GO) and establish pathway associations for 230 (33%) sequences using the Kyoto Encyclopedia of Genes and Genomes (KEGG). Further examination of this EST dataset revealed a number of signalling molecules, proteases, protease inhibitors, enzymes, ion channels and immune-related genes. In addition, we identified 40 putative secreted proteins that could represent potential candidates for developing novel anthelmintics or vaccines. We further compared the automated EST sequence annotations, using ESTExplorer, with database search results for individual T. vitrinus ESTs. ESTExplorer reliably and rapidly annotated 301 ESTs, with pathway and GO information, eliminating 60 low quality hits from database searches.ConclusionWe evaluated the efficacy of ESTExplorer in analysing EST data, and demonstrate that computational tools can be used to accelerate the process of gene discovery in EST sequencing projects. The present study has elucidated sets of relatively conserved and potentially novel genes for biological investigation, and the annotated EST set provides further insight into the molecular biology of T. vitrinus, towards the identification of novel drug targets.</str>
            </arr>
            <str name="_version_">1421611432358707200</str>
        </doc>
        <doc>
            <str name="id">946e286d-fbf9-477a-8982-936df4059dde</str>
            <str name="article-title">Structural and evolutionary bioinformatics of the SPOUT superfamily of methyltransferases</str>
            <arr name="content">
                <str>Structural and evolutionary bioinformatics of the SPOUT superfamily of methyltransferases</str>
                <str>BackgroundSPOUT methyltransferases (MTases) are a large class of S-adenosyl-L-methionine-dependent enzymes that exhibit an unusual alpha/beta fold with a very deep topological knot. In 2001, when no crystal structures were available for any of these proteins, Anantharaman, Koonin, and Aravind identified homology between SpoU and TrmD MTases and defined the SPOUT superfamily. Since then, multiple crystal structures of knotted MTases have been solved and numerous new homologous sequences appeared in the databases. However, no comprehensive comparative analysis of these proteins has been carried out to classify them based on structural and evolutionary criteria and to guide functional predictions.ResultsWe carried out extensive searches of databases of protein structures and sequences to collect all members of previously identified SPOUT MTases, and to identify previously unknown homologs. Based on sequence clustering, characterization of domain architecture, structure predictions and sequence/structure comparisons, we re-defined families within the SPOUT superfamily and predicted putative active sites and biochemical functions for the so far uncharacterized members. We have also delineated the common core of SPOUT MTases and inferred a multiple sequence alignment for the conserved knot region, from which we calculated the phylogenetic tree of the superfamily. We have also studied phylogenetic distribution of different families, and used this information to infer the evolutionary history of the SPOUT superfamily.ConclusionWe present the first phylogenetic tree of the SPOUT superfamily since it was defined, together with a new scheme for its classification, and discussion about conservation of sequence and structure in different families, and their functional implications. We identified four protein families as new members of the SPOUT superfamily. Three of these families are functionally uncharacterized (COG1772, COG1901, and COG4080), and one (COG1756 represented by Nep1p) has been already implicated in RNA metabolism, but its biochemical function has been unknown. Based on the inference of orthologous and paralogous relationships between all SPOUT families we propose that the Last Universal Common Ancestor (LUCA) of all extant organisms contained at least three SPOUT members, ancestors of contemporary RNA MTases that carry out m1G, m3U, and 2'O-ribose methylation, respectively. In this work we also speculate on the origin of the knot and propose possible 'unknotted' ancestors. The results of our analysis provide a comprehensive 'roadmap' for experimental characterization of SPOUT MTases and interpretation of functional studies in the light of sequence-structure relationships.</str>
            </arr>
            <str name="file">BMC_Bioinformatics_2007_Mar_5_8_73.nxml</str>
            <arr name="article-abstract">
                <str>BackgroundSPOUT methyltransferases (MTases) are a large class of S-adenosyl-L-methionine-dependent enzymes that exhibit an unusual alpha/beta fold with a very deep topological knot. In 2001, when no crystal structures were available for any of these proteins, Anantharaman, Koonin, and Aravind identified homology between SpoU and TrmD MTases and defined the SPOUT superfamily. Since then, multiple crystal structures of knotted MTases have been solved and numerous new homologous sequences appeared in the databases. However, no comprehensive comparative analysis of these proteins has been carried out to classify them based on structural and evolutionary criteria and to guide functional predictions.ResultsWe carried out extensive searches of databases of protein structures and sequences to collect all members of previously identified SPOUT MTases, and to identify previously unknown homologs. Based on sequence clustering, characterization of domain architecture, structure predictions and sequence/structure comparisons, we re-defined families within the SPOUT superfamily and predicted putative active sites and biochemical functions for the so far uncharacterized members. We have also delineated the common core of SPOUT MTases and inferred a multiple sequence alignment for the conserved knot region, from which we calculated the phylogenetic tree of the superfamily. We have also studied phylogenetic distribution of different families, and used this information to infer the evolutionary history of the SPOUT superfamily.ConclusionWe present the first phylogenetic tree of the SPOUT superfamily since it was defined, together with a new scheme for its classification, and discussion about conservation of sequence and structure in different families, and their functional implications. We identified four protein families as new members of the SPOUT superfamily. Three of these families are functionally uncharacterized (COG1772, COG1901, and COG4080), and one (COG1756 represented by Nep1p) has been already implicated in RNA metabolism, but its biochemical function has been unknown. Based on the inference of orthologous and paralogous relationships between all SPOUT families we propose that the Last Universal Common Ancestor (LUCA) of all extant organisms contained at least three SPOUT members, ancestors of contemporary RNA MTases that carry out m1G, m3U, and 2'O-ribose methylation, respectively. In this work we also speculate on the origin of the knot and propose possible 'unknotted' ancestors. The results of our analysis provide a comprehensive 'roadmap' for experimental characterization of SPOUT MTases and interpretation of functional studies in the light of sequence-structure relationships.</str>
            </arr>
            <str name="_version_">1421611432359755776</str>
        </doc>
        <doc>
            <str name="id">331064c1-80d4-488a-a7d2-8fa9f6cdeaa9</str>
            <str name="article-title">A method for the prediction of GPCRs coupling specificity to G-proteins using refined profile Hidden Markov Models</str>
            <arr name="content">
                <str>A method for the prediction of GPCRs coupling specificity to G-proteins using refined profile Hidden Markov Models</str>
                <str>BackgroundG- Protein coupled receptors (GPCRs) comprise the largest group of eukaryotic cell surface receptors with great pharmacological interest. A broad range of native ligands interact and activate GPCRs, leading to signal transduction within cells. Most of these responses are mediated through the interaction of GPCRs with heterotrimeric GTP-binding proteins (G-proteins). Due to the information explosion in biological sequence databases, the development of software algorithms that could predict properties of GPCRs is important. Experimental data reported in the literature suggest that heterotrimeric G-proteins interact with parts of the activated receptor at the transmembrane helix-intracellular loop interface. Utilizing this information and membrane topology information, we have developed an intensive exploratory approach to generate a refined library of statistical models (Hidden Markov Models) that predict the coupling preference of GPCRs to heterotrimeric G-proteins. The method predicts the coupling preferences of GPCRs to Gs, Gi/o and Gq/11, but not G12/13 subfamilies.ResultsUsing a dataset of 282 GPCR sequences of known coupling preference to G-proteins and adopting a five-fold cross-validation procedure, the method yielded an 89.7% correct classification rate. In a validation set comprised of all receptor sequences that are species homologues to GPCRs with known coupling preferences, excluding the sequences used to train the models, our method yields a correct classification rate of 91.0%. Furthermore, promiscuous coupling properties were correctly predicted for 6 of the 24 GPCRs that are known to interact with more than one subfamily of G-proteins.ConclusionOur method demonstrates high correct classification rate. Unlike previously published methods performing the same task, it does not require any transmembrane topology prediction in a preceding step. A web-server for the prediction of GPCRs coupling specificity to G-proteins available for non-commercial users is located at .</str>
            </arr>
            <str name="file">BMC_Bioinformatics_2005_Apr_22_6_104.nxml</str>
            <arr name="article-abstract">
                <str>BackgroundG- Protein coupled receptors (GPCRs) comprise the largest group of eukaryotic cell surface receptors with great pharmacological interest. A broad range of native ligands interact and activate GPCRs, leading to signal transduction within cells. Most of these responses are mediated through the interaction of GPCRs with heterotrimeric GTP-binding proteins (G-proteins). Due to the information explosion in biological sequence databases, the development of software algorithms that could predict properties of GPCRs is important. Experimental data reported in the literature suggest that heterotrimeric G-proteins interact with parts of the activated receptor at the transmembrane helix-intracellular loop interface. Utilizing this information and membrane topology information, we have developed an intensive exploratory approach to generate a refined library of statistical models (Hidden Markov Models) that predict the coupling preference of GPCRs to heterotrimeric G-proteins. The method predicts the coupling preferences of GPCRs to Gs, Gi/o and Gq/11, but not G12/13 subfamilies.ResultsUsing a dataset of 282 GPCR sequences of known coupling preference to G-proteins and adopting a five-fold cross-validation procedure, the method yielded an 89.7% correct classification rate. In a validation set comprised of all receptor sequences that are species homologues to GPCRs with known coupling preferences, excluding the sequences used to train the models, our method yields a correct classification rate of 91.0%. Furthermore, promiscuous coupling properties were correctly predicted for 6 of the 24 GPCRs that are known to interact with more than one subfamily of G-proteins.ConclusionOur method demonstrates high correct classification rate. Unlike previously published methods performing the same task, it does not require any transmembrane topology prediction in a preceding step. A web-server for the prediction of GPCRs coupling specificity to G-proteins available for non-commercial users is located at .</str>
            </arr>
            <str name="_version_">1421611432361852928</str>
        </doc>
    </result>
    <lst name="sbtagger">
        <lst name="f73ca075-3826-45d5-85df-64b33c760efc">
            <double name="functional sites binding peptides ">25.301286487525626</double>
            <double name="backgroundthe functional sites ">20.905676086270567</double>
            <double name="functional sites ">19.576857447385397</double>
            <double name="protein domains.resultsfunctional sites ">17.52993106174624</double>
            <double name="binding sites ">16.724235671718475</double>
        </lst>
        <lst name="bc72dbef-87d1-4c39-b388-ec67babe6f05">
            <double name="metastasis candidate genes ">23.587185453763603</double>
            <double name="metastasis unrelated test combinations ">20.78138471504425</double>
            <double name="metastasis characteristics ">20.31368409807749</double>
            <double name="mining metastasis ">16.86337841122389</double>
            <double name="metastasis ">15.424716518399412</double>
        </lst>
        <lst name="bfdb855d-465c-479e-94ef-9567b178788d">
            <double name="pore stoichiometry ">214.7534909671297</double>
            <double name="pore formation ">213.08139310919398</double>
            <double name="pore ">211.23294193488167</double>
            <double name="backgroundalpha-helical transmembrane channel ">16.84666343655723</double>
            <double name="transmembrane proteins ">13.962496266044742</double>
        </lst>
        <lst name="724e4696-2f5e-47f0-b782-d28feeb64d56">
            <double name="go-based global functional profiling ">17.023295697411285</double>
            <double name="go-based functional profiling approach ">16.62104044901614</double>
            <double name="est-based gene expression analysis ">13.402105581441836</double>
            <double name="comparative transcriptomics ">10.720192460906194</double>
            <double name="est-based transcriptomes ">10.362269222376767</double>
        </lst>
        <lst name="0d133582-62f0-4f30-9643-7f83705fe2c5">
            <double name="backgroundthe pathologist ">12.897459047676035</double>
            <double name="robustly differentiate breast cancer cell lines ">11.592095003497324</double>
            <double name="pathologist ">11.568640408790865</double>
            <double name="molecular interactions.results molecular abundance data ">11.211470785193955</double>
            <double name="pathway behavior ">10.487807315780461</double>
        </lst>
        <lst name="4c2944b7-dc82-422e-ba9b-ea48fbf402b3">
            <double name="scientific information ">11.632103199663144</double>
            <double name="academic publishing ">10.686996104741532</double>
            <double name="academic journals ">10.669007096174866</double>
            <double name="optimal information architecture ">10.359801912470132</double>
            <double name="scientific articles ">10.078818892512597</double>
        </lst>
        <lst name="f3a8183c-d074-4220-8a54-74a79fdfac62">
            <double name="human adipose triglyceride lipase ">22.856628879661272</double>
            <double name="adipose triglyceride lipase ">22.052083191048045</double>
            <double name="sensitive sequence segment collection heuristic suitable ">21.058977797013746</double>
            <double name="classical mammalian lipases ">19.991472899774976</double>
            <double name="classical lipase family ">18.21658549151753</double>
        </lst>
        <lst name="9a6f4fb4-2836-42a7-be77-34418d2aea55">
            <double name="estexplorer workflow platform ">32.325977530683765</double>
            <double name="estexplorer ">26.751053195384063</double>
            <double name="parasitic nematodes ">12.285733257435364</double>
            <double name="semi-automated computational workflow system ">11.308682750588503</double>
            <double name="parasitic nematode trichostrongylus ">9.467758353792181</double>
        </lst>
        <lst name="946e286d-fbf9-477a-8982-936df4059dde">
            <double name="knotted mtases ">28.164392257984222</double>
            <double name="trmd mtases ">24.643843225736195</double>
            <double name="spout mtases ">24.643843225736195</double>
            <double name="mtases ">24.643843225736195</double>
            <double name="deep topological knot ">14.27689650633362</double>
        </lst>
        <lst name="331064c1-80d4-488a-a7d2-8fa9f6cdeaa9">
            <double name="gpcrs coupling specificity ">46.30710641787624</double>
            <double name="activate gpcrs ">30.085022427320872</double>
            <double name="gpcrs ">27.07635675605172</double>
            <double name="heterotrimeric g-proteins interact ">22.846830178147908</double>
            <double name="promiscuous coupling properties ">22.217915437639363</double>
        </lst>
    </lst>
</response>
	
We can see that we have asked for tags for the first 10 documents using
the query "*:*". for  a pubmed dataset (a medical research publication 
repository). As expected we see see a list of responses in the sbtagger xml object. 
The name of the entity is printed with its score.  The responses which appear higher 
in the list are of greater chance (and therefore quality) as a tags for the query document.

Enjoy!

