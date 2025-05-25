<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="fr.sparna.rdf.skosplay.SessionData" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" 	prefix="fmt" 	%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" 	prefix="c" 		%>

<!-- setup the locale for the messages based on the language in the session -->
<fmt:setLocale value="${sessionScope['fr.sparna.rdf.skosplay.SessionData'].userLocale.language}"/>
<fmt:setBundle basename="fr.sparna.rdf.skosplay.i18n.Bundle"/>

<html>
	<head>
		<title><c:out value="${applicationData.skosPlayConfig.applicationTitle}" /></title>
		<link rel="canonical" href="https://skos-play.sparna.fr/play/home?lang=en" />

		<meta http-equiv="content-type" content="text/html; charset=UTF-8">
		<link href="bootstrap/css/bootstrap.min.css" rel="stylesheet" />
		<link href="bootstrap-fileupload/bootstrap-fileupload.min.css" rel="stylesheet" />
		<link href="css/skos-play.css" rel="stylesheet" />
		<link href="style/custom.css" rel="stylesheet" />
		<script src="js/jquery.min.js"></script>
		<script src="bootstrap/js/bootstrap.min.js"></script>
		<script src="bootstrap-fileupload/bootstrap-fileupload.min.js"></script>
		
		<link rel="alternate" hreflang="fr" href="?lang=fr" />
	</head>
	<body>
		<div class="container">
			<%-- see http://stackoverflow.com/questions/19150683/passing-parameters-to-another-jsp-file-in-jspinclude --%>
			<jsp:include page="header.jsp">
				<jsp:param name="active" value="home"/>
			</jsp:include>
			
			<div style="font-size:1.6em; line-height: 1.3em; text-align:justify;">
				<p>
				SKOS Play is a free application to render and visualise thesaurus, taxonomies or controlled vocabularies expressed in 
				<a href="http://www.w3.org/TR/2009/REC-skos-reference-20090818/" target="_blank">SKOS</a>.
				</p>
				<p>
				With SKOS Play you can print Knowledge Organization Systems that use the SKOS data model in HTML or PDF documents, and
				visualize them in graphical representations.
				</p>
				<p>
				SKOS Play can be used :
				</p>
				<ul>
					<li style="line-height: 1.3em;">to <em>test and verify</em> a vocabulary during the conception phase</li>
					<li style="line-height: 1.3em;">to <em>exchange and communicate</em> the vocabulary when validating it with domain experts</li>
					<li style="line-height: 1.3em;">to <em>publish</em> it when it is shared on the web.</li>
				</ul> 
				<br />
				<div style="text-align:center">
                  	<a href="upload"><button class="btn btn-primary btn-lg ">Start Here</button></a>
                  	<a href="about"><button class="btn btn-default btn-lg" type="button">Learn More</button></a>                
              	</div>
              	<hr />
              	<p>SKOS Play also provides a way to convert Excel spreadsheets to <a href="http://www.w3.org/TR/2009/REC-skos-reference-20090818/" target="_blank">SKOS</a> files.</p>
              	<p>This makes it easy to produce SKOS files for taxonomies and authorities list.</p>
              	<p>The converter can even generate data in other RDF vocabularies.</p>
				<br />
              	<div style="text-align:center">
					<a href="convert"><button class="btn btn-info btn-lg ">Convert Excel to SKOS</button></a>         
              	</div>
              	<hr />
              	<p><span style="color: #38b44a;"><span class="glyphicon glyphicon glyphicon-certificate" aria-hidden="true"></span></span>&nbsp;Now you can also validate your SKOS data using the <strong><a href="http://labs.sparna.fr/skos-testing-tool">SKOS Testing Tool</a></strong>.</p>
              	<p>The SKOS Testing Tool is a frontend for <a href="https://github.com/cmader/qSKOS" target="_blank">qSKOS</a> by Christian Mader.</p>
				<br />
              	<div style="text-align:center">
					<a href="http://labs.sparna.fr/skos-testing-tool"><button class="btn btn-success btn-lg ">SKOS Testing Tool</button></a>         
              	</div>
              	<br />
              	<br />
			</div>
      	</div>
      	<jsp:include page="footer.jsp" />
	</body>
</html>