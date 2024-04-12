package fr.sparna.rdf.skosplay;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.security.InvalidParameterException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.DC;
import org.eclipse.rdf4j.model.vocabulary.DCTERMS;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParserRegistry;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.RDFWriterRegistry;
import org.eclipse.rdf4j.rio.Rio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import fr.sparna.commons.io.ReadWriteTextFile;
import fr.sparna.commons.tree.GenericTree;
import fr.sparna.commons.tree.GenericTreeNode;
import fr.sparna.i18n.StrictResourceBundleControl;
import fr.sparna.rdf.rdf4j.toolkit.query.Perform;
import fr.sparna.rdf.rdf4j.toolkit.query.SimpleQueryReader;
import fr.sparna.rdf.rdf4j.toolkit.reader.TypeReader;
import fr.sparna.rdf.rdf4j.toolkit.repository.RepositoryBuilderFactory;
import fr.sparna.rdf.rdf4j.toolkit.util.LabelReader;
import fr.sparna.rdf.rdf4j.toolkit.util.PreferredPropertyReader;
import fr.sparna.rdf.skos.printer.DisplayPrinter;
import fr.sparna.rdf.skos.printer.autocomplete.Items;
import fr.sparna.rdf.skos.printer.autocomplete.JSONWriter;
import fr.sparna.rdf.skos.printer.freemind.schema.Map;
import fr.sparna.rdf.skos.printer.freemind.schema.Node;
import fr.sparna.rdf.skos.printer.reader.AbstractKosDisplayGenerator;
import fr.sparna.rdf.skos.printer.reader.AlignmentDataHarvesterCachedLoader;
import fr.sparna.rdf.skos.printer.reader.AlignmentDataHarvesterIfc;
import fr.sparna.rdf.skos.printer.reader.AlignmentDisplayGenerator;
import fr.sparna.rdf.skos.printer.reader.AlphaIndexDisplayGenerator;
import fr.sparna.rdf.skos.printer.reader.AutocompleteItemsReader;
import fr.sparna.rdf.skos.printer.reader.BodyReader;
import fr.sparna.rdf.skos.printer.reader.ConceptBlockReader;
import fr.sparna.rdf.skos.printer.reader.ConceptListDisplayGenerator;
import fr.sparna.rdf.skos.printer.reader.HeaderAndFooterReader;
import fr.sparna.rdf.skos.printer.reader.HierarchicalDisplayGenerator;
import fr.sparna.rdf.skos.printer.reader.IndexGenerator;
import fr.sparna.rdf.skos.printer.reader.IndexGenerator.IndexType;
import fr.sparna.rdf.skos.printer.reader.TranslationTableDisplayGenerator;
import fr.sparna.rdf.skos.printer.reader.TranslationTableReverseDisplayGenerator;
import fr.sparna.rdf.skos.printer.schema.KosDocument;
import fr.sparna.rdf.skos.toolkit.JsonSKOSTreePrinter;
import fr.sparna.rdf.skos.toolkit.SKOS;
import fr.sparna.rdf.skos.toolkit.SKOSNodeSortCriteriaPreferredPropertyReader;
import fr.sparna.rdf.skos.toolkit.SKOSNodeTypeReader;
import fr.sparna.rdf.skos.toolkit.SKOSTreeBuilder;
import fr.sparna.rdf.skos.toolkit.SKOSTreeNode;
import fr.sparna.rdf.skos.toolkit.SKOSTreeNode.NodeType;
import fr.sparna.rdf.skosplay.log.LogEntry;




/**
 * The main entry point.
 * @Controller indicates this class will be the application controller, the main entry point.
 * 
 * To add an extra RequestMapping here, add the corresponding path to web.xml mappings.
 * 
 * @author Thomas Francart
 *
 */
@Controller
public class SkosPlayController {

	private Logger log = LoggerFactory.getLogger(this.getClass().getName());

	@Autowired
	protected ServletContext servletContext;

	private enum SOURCE_TYPE {
		FILE,
		URL,
		EXAMPLE
	}
	

	@RequestMapping("/home")
	public ModelAndView home(HttpServletRequest request) {	
		if(SkosPlayConfig.getInstance().isPublishingMode()) {
			// if publishing mode, no home page
			return uploadForm();

		} else {
			// retrieve resource bundle for path to home page
			ResourceBundle b = ResourceBundle.getBundle(
					"fr.sparna.rdf.skosplay.i18n.Bundle",
					SessionData.get(request.getSession()).getUserLocale(),
					new StrictResourceBundleControl()
					);

			return new ModelAndView(b.getString("home.jsp"));
		}		
	}

	@RequestMapping("/about")
	public ModelAndView about(HttpServletRequest request) {

		// retrieve resource bundle for error messages
		ResourceBundle b = ResourceBundle.getBundle(
				"fr.sparna.rdf.skosplay.i18n.Bundle",
				SessionData.get(request.getSession()).getUserLocale(),
				new StrictResourceBundleControl()
				);

		return new ModelAndView(b.getString("about.jsp"));
	}

	@RequestMapping("/style/custom.css")
	public void style(
			HttpServletRequest request,
			HttpServletResponse response
			) throws Exception {	

		if(SkosPlayConfig.getInstance().getCustomCss() != null) {
			try {
				log.debug("Reading and returning custom CSS from "+SkosPlayConfig.getInstance().getCustomCss());
				String content = ReadWriteTextFile.getContents(SkosPlayConfig.getInstance().getCustomCss());
				response.getOutputStream().write(content.getBytes());
				response.flushBuffer();
			} catch (FileNotFoundException e) {
				// should not happen
				throw e;
			} catch (IOException e) {
				log.error("Exception while reading custom CSS from "+SkosPlayConfig.getInstance().getCustomCss().getAbsolutePath());
				throw e;
			}
		}
	}

	@RequestMapping(value = "/upload", method = RequestMethod.GET)
	public ModelAndView uploadForm() {
		// set an empty Model - just so that JSP can access SkosPlayConfig through it
		UploadFormData data = new UploadFormData();
		return new ModelAndView("upload", UploadFormData.KEY, data);
	}



	@RequestMapping(value = "/upload", method = RequestMethod.POST)
	public ModelAndView upload(
			// radio box indicating type of input
			@RequestParam(value="source", required=true) String sourceString,
			// uploaded file if source=file
			@RequestParam(value="file", required=false) MultipartFile file,
			// reference example if source=example
			@RequestParam(value="example", required=false) String example,
			// url of file or SPARQL endpoint if source=url
			@RequestParam(value="url", required=false) String url,
			// flag indicating to apply RDFS inference or not
			@RequestParam(value="rdfsInference", required=false) boolean rdfsInference,
			// flag indicating to transform OWL to SKOS
			@RequestParam(value="owl2skos", required=false) boolean owl2skos,
			// flag indicating to transform SKOS-XL to SKOS
			@RequestParam(value="skosxl2skos", required=false) boolean skosxl2skos,
			HttpServletRequest request
			) throws IOException, URISyntaxException {
		
		log.debug("upload(source="+sourceString+",example="+example+",url="+url+",rdfsInference="+rdfsInference+", owl2skos="+owl2skos+")");

		// get the source
		SOURCE_TYPE source = SOURCE_TYPE.valueOf(sourceString.toUpperCase());		

		// retrieve session
		final SessionData sessionData = SessionData.get(request.getSession());
		// prepare data structure
		final PrintFormData printFormData = new PrintFormData();
		sessionData.setPrintFormData(printFormData);
		int count = -1;
		
		// retrieve resource bundle for error messages
		ResourceBundle b = ResourceBundle.getBundle(
				"fr.sparna.rdf.skosplay.i18n.Bundle",
				sessionData.getUserLocale(),
				new StrictResourceBundleControl()
				);

		SkosPlayModel skosPlayModel = new SkosPlayModel();
		try {
			switch(source) {
			case FILE : {
				// get uploaded file
				if(file.isEmpty()) {
					return doError(request, "Uploaded file is empty");
				}
				
				// explicitely remove TRIX format from RDFParserRegistry
				if(RDFParserRegistry.getInstance().has(RDFFormat.TRIX)) {
					RDFParserRegistry.getInstance().remove(RDFParserRegistry.getInstance().get(RDFFormat.TRIX).get());
				}
				
				RDFFormat rdfFormat = RDFParserRegistry.getInstance().getFileFormatForFileName(file.getOriginalFilename()).orElse(RDFFormat.RDFXML);				
				log.debug("Uploaded file name is \""+file.getOriginalFilename()+"\", will be parsed as "+rdfFormat.getName());
				skosPlayModel.load(
						file.getInputStream(),
						rdfFormat,
						rdfsInference
				);
				skosPlayModel.setInputFileName(file.getOriginalFilename());
				try {
					if(owl2skos) {
						skosPlayModel.performOwl2Skos();					
					}
					if(skosxl2skos) {
						skosPlayModel.performSkosXl2Skos();
					}
				} catch (Exception e1) {
					return doError(request, e1.getMessage());
				}

				break;
			}			
			case EXAMPLE : {
				// get resource param
				String resourceParam = example;
				
				if(resourceParam == null || resourceParam.equals("")) {
					return doError(request, "Select an example from the list.");
				}
				skosPlayModel.loadExample(resourceParam);

				// apply rules if needed
				try {
					if(owl2skos) {
						skosPlayModel.performOwl2Skos();					
					}
					if(skosxl2skos) {
						skosPlayModel.performSkosXl2Skos();
					}
				} catch (Exception e1) {
					return doError(request, e1.getMessage());
				}

				// set the loaded data name
				try {
					printFormData.setLoadedDataName(sessionData.getPreLoadedDataLabels().getString(example));
				} catch (Exception e) {
					// missing label : set the key
					printFormData.setLoadedDataName(example);
				}

				break;
			}
			case URL : {	
				skosPlayModel.load(url, rdfsInference);
				skosPlayModel.setInputUrl(url);
				
				// we are loading an RDF file from the web, use the localRepositoryBuilder and apply inference if required
				if(!RepositoryBuilderFactory.isEndpointURL(url)) {
					try {
						if(owl2skos) {
							skosPlayModel.performOwl2Skos();					
						}
						if(skosxl2skos) {
							skosPlayModel.performSkosXl2Skos();
						}
					} catch (Exception e1) {
						return doError(request, e1.getMessage());
					}
				}
				
				break;
			}

			}
		} catch (Exception e) {
			return doError(request, e);
		}



		// store repository in the session
		sessionData.setSkosPlayModel(skosPlayModel);
		
		try {			
			
			// check that data does not contain more than X concepts
			count = skosPlayModel.getConceptCount();
			// check that data contains at least one SKOS Concept
			if(count <= 0) {
				return doError(request, b.getString("upload.error.noConceptsFound"));
			}

			int limitConfiguration = SkosPlayConfig.getInstance().getConceptsLimit();
			if(
					source != SOURCE_TYPE.EXAMPLE
					&&
					limitConfiguration > 0
					&&
					count > limitConfiguration
					) {
				return doError(
						request,
						MessageFormat.format(
								b.getString("upload.error.dataTooLarge"),
								limitConfiguration
								)
						);
			}			

		} catch (Exception e) {
			e.printStackTrace();
			return doError(request, e);
		}

		// set loaded data licence, if any
		printFormData.setLoadedDataLicense(skosPlayModel.getLicense());

		try(RepositoryConnection connection = skosPlayModel.getRepository().getConnection()) {
			// store sourceConceptLabel reader in the session
			// default to no language
			final LabelReader labelReader = new LabelReader(connection, "", sessionData.getUserLocale().getLanguage());
			// add dcterms title and dc title
			labelReader.getProperties().add(DCTERMS.TITLE);
			labelReader.getProperties().add(DC.TITLE);
	
			// store success message with number of concepts
			printFormData.setSuccessMessage(MessageFormat.format(b.getString("print.message.numberOfConcepts"), count));
	
			if(DisplayType.needHierarchyCheck() || VizType.needHierarchyCheck()) {
				try {
					// ask if some hierarchy exists
					if(!skosPlayModel.isHierarchical()) {
						printFormData.setEnableHierarchical(false);
						printFormData.getWarningMessages().add(b.getString("upload.warning.noHierarchyFound"));
					}
				} catch (Exception e) {
					printFormData.setEnableHierarchical(false);
					printFormData.getWarningMessages().add(b.getString("upload.warning.noHierarchyFound"));
				}
			}
	
			if(DisplayType.needTranslationCheck()) {
				try {
					// ask if some translations exists
					if(!skosPlayModel.isMultilingual()) {
						printFormData.setEnableTranslations(false);
						printFormData.getWarningMessages().add(b.getString("upload.warning.noTranslationsFound"));
					}
				} catch (Exception e) {
					printFormData.setEnableTranslations(false);
					printFormData.getWarningMessages().add(b.getString("upload.warning.noTranslationsFound"));
				}
			}
	
			if(DisplayType.needAlignmentCheck()) {
				try {
					// ask if some alignments exists
					if(!skosPlayModel.isAligned()) {
						printFormData.setEnableMappings(false);
						printFormData.getWarningMessages().add(b.getString("upload.warning.noMappingsFound"));
					}
				} catch (Exception e) {
					printFormData.setEnableMappings(false);
					printFormData.getWarningMessages().add(b.getString("upload.warning.noMappingsFound"));
				}
			}
		

			// retrieve number of concepts per concept schemes
			printFormData.setConceptCountByConceptSchemes(skosPlayModel.getConceptCountByConceptScheme(labelReader));
			
			// retrieve list of declared languages in the data
			printFormData.setLanguages(skosPlayModel.getLanguages(sessionData.getUserLocale().getLanguage()));

		} catch (Exception e) {
			return doError(request, e);
		}


		return new ModelAndView("print");
	}

	protected ModelAndView doError(
			HttpServletRequest request,
			Exception e
			) {
		// print stack trace
		e.printStackTrace();
		// build on-screen error message
		StringBuffer message = new StringBuffer(e.getMessage());
		Throwable current = e.getCause();
		while(current != null) {
			message.append(". Cause : "+current.getMessage());
			current = current.getCause();
		}
		return doError(request, message.toString());
	}
	
	protected ModelAndView doError(
			HttpServletRequest request,
			String message
			) {
		UploadFormData data = new UploadFormData();
		data.setErrorMessage(message);
		request.setAttribute(UploadFormData.KEY, data);
		return new ModelAndView("upload");
	}
	
	@RequestMapping(value = "/getData")
	protected void getData(
			HttpServletRequest request,
			HttpServletResponse response
			) throws IOException {
		response.addHeader("Content-Encoding", "UTF-8");
		response.addHeader("Content-Disposition", "attachment;filename=\"owl2skos.ttl\"");
		response.setContentType("text/turtle");
		// write the content of the temporary repository into an in-memory Turtle representation
		RDFWriter writer = RDFWriterRegistry.getInstance().get(RDFFormat.TURTLE).get().getWriter(response.getOutputStream());
		// retrieve data from session
		Repository r = SessionData.get(request.getSession()).getSkosPlayModel().getRepository();
		try(RepositoryConnection connectionTemporaire = r.getConnection()) {
			connectionTemporaire.export(writer);
		}
		
		// flush response
		response.flushBuffer();
	}


	@RequestMapping(
			value = "/visualize",
			method = RequestMethod.POST
			)
	public ModelAndView visualize(
			// output type, PDF or HTML
			@RequestParam(value="display", required=true) String displayParam,
			@RequestParam(value="language", defaultValue="no-language") String language,
			@RequestParam(value="scheme", defaultValue="no-scheme") String schemeParam,
			HttpServletRequest request,
			HttpServletResponse response
			) throws Exception {

		// get viz type param
		VizType displayType = (displayParam != null)?VizType.valueOf(displayParam.toUpperCase()):null;

		// get scheme param
		IRI scheme = (schemeParam.equals("no-scheme"))?null:SimpleValueFactory.getInstance().createIRI(schemeParam);

		// update source language param - only for translations
		language = (language.equals("no-language"))?null:language;
		SessionData sessionData = SessionData.get(request.getSession());
		
		// retrieve data from session
		Repository r = sessionData.getSkosPlayModel().getRepository();

		// make a log to trace usage
		try(RepositoryConnection connection = r.getConnection()) {
			String aRandomConcept = Perform.on(connection).read(new SimpleQueryReader(this, "ReadRandomConcept.rq").get()).stringValue();
			log.info("PRINT,"+SimpleDateFormat.getDateTimeInstance().format(new Date())+","+scheme+","+aRandomConcept+","+language+","+displayType+","+"HTML");		
			
			SkosPlayConfig.getInstance().getSqlLogDao().insertLog(new LogEntry(
					language,
					"datavize",
					displayParam,
					sessionData.getSkosPlayModel().getInputUrl(),
					"print",
					schemeParam)
			);
	
			
			switch(displayType) {
			case PARTITION : {		
				request.setAttribute("dataset", generateJSON(connection, language, scheme));
				// forward to the JSP
				return new ModelAndView("viz-partition");
			}
			case TREELAYOUT : {
				request.setAttribute("dataset", generateJSON(connection, language, scheme));
				// forward to the JSP
				return new ModelAndView("viz-treelayout");
			}
			case SUNBURST : {
				request.setAttribute("dataset", generateJSON(connection, language, scheme));
				// forward to the JSP
				return new ModelAndView("viz-sunburst");
			}
			/*case TREEMAP : {
				request.setAttribute("dataset", generateJSON(r, language, scheme));
				// forward to the JSP
				return new ModelAndView("viz-treemap");
			}*/
			case AUTOCOMPLETE : {
				AutocompleteItemsReader autocompleteReader = new AutocompleteItemsReader();
				Items items = autocompleteReader.readItems(r, language, scheme);
				JSONWriter writer = new JSONWriter();
				request.setAttribute("items", writer.write(items));
				// forward to the JSP
				return new ModelAndView("viz-autocomplete");
			}
			case FREEMIND : {
				response.setContentType("application/xml");
                response.setHeader("Content-Disposition", "inline; filename=\""+"freemind-export.mm");
                
                
                Map m = new Map();
                Node root = new Node();
                m.setNode(root);
                root.setCreated("1708696092718");
                root.setModified("1708696092718");
                root.setId("1");
                root.setText("Thésaurus du SMT !");
                
                Node node1 = new Node();
                node1.setCreated("1708696092718");
                node1.setModified("1708696092718");
                node1.setId("2");
                node1.setText("node 1");
                
                Node node2 = new Node();
                node2.setCreated("1708696092718");
                node2.setModified("1708696092718");
                node2.setId("3");
                node2.setText("node 2");
                
                List<Node> nodes = new ArrayList<Node>();
                nodes.add(node1);
                nodes.add(node2);
                
                root.setChildrens(nodes);
                
                OutputStreamWriter w = new OutputStreamWriter(response.getOutputStream());
                JAXBContext context = JAXBContext.newInstance(Map.class);
                Marshaller marsh = context.createMarshaller();
                marsh.marshal(m, w);
                
                w.close();
                response.getOutputStream().flush();
                
                /*
                String FAKE =  "<map version=\"1.0.1\">"
                		+ "<node CREATED=\"1706716720958\" ID=\"ID_1328781019\" MODIFIED=\"1708696092718\" TEXT=\"Thésaurus du STM\"></node></map>";
                
                OutputStreamWriter w = new OutputStreamWriter(response.getOutputStream());
                w.write(FAKE);
                w.close();
                response.getOutputStream().flush();
                */
                
                return null;
			}
			
			default : {
				throw new InvalidParameterException("Unknown display type "+displayType);
			}
			
			}	
		}
		
	}

	/**
	 * Print API that takes directy a URL as an input
	 * 
	 * @param url
	 * @param displayParam
	 * @param outputParam
	 * @param languageParam
	 * @param schemeParam
	 * @param targetLanguageParam
	 * @param request
	 * @param response
	 * @throws Exception
	 */
	@RequestMapping(value = "/print", method = RequestMethod.GET, params = {"url"})
	public void printApi(			
			@RequestParam(value="url", required=true) String url,
			@RequestParam(value="display", required=false) String displayParam,
			// output type, PDF or HTML
			@RequestParam(value="output", required=false) String outputParam,			
			@RequestParam(value="language", required=false) String languageParam,
			@RequestParam(value="scheme", defaultValue="no-scheme") String schemeParam,
			@RequestParam(value="targetLanguage", defaultValue="no-language") String targetLanguageParam,
			HttpServletRequest request,
			HttpServletResponse response
	) throws Exception {
		
		SkosPlayModel skosPlayModel = new SkosPlayModel();
		// rdfsInference : false
		skosPlayModel.load(url, false);
		skosPlayModel.setInputUrl(url);
		// handle SKOS-XL by default
		skosPlayModel.performSkosXl2Skos();
		// get repository back
		Repository r = skosPlayModel.getRepository();

		// read all languages
		Set<String> languages = skosPlayModel.getLanguages();
		
		// get output type param
		OutputType outputType = (outputParam != null)?OutputType.valueOf(outputParam.toUpperCase()):OutputType.HTML;

		// get display type param
		DisplayType displayType = (displayParam != null)?DisplayType.valueOf(displayParam.toUpperCase()):DisplayType.ALPHABETICAL_EXPANDED;

		// get scheme param
		IRI scheme = (schemeParam.equals("no-scheme"))?null:SimpleValueFactory.getInstance().createIRI(schemeParam);

		// update source language param - only for translations
		String language = (languageParam == null || languageParam.equals("no-language"))?null:languageParam;
		if(language == null) {
			// try to determine language
			if(languages.size() == 1) {
				language = languages.iterator().next();
			} else {
				throw new Exception("Please set language param, found "+languages.size()+" different languages, don't know which one to use : "+languages+"");
			}
		}

		// get target language param - only for translations
		String targetLanguage = (targetLanguageParam != null)?(targetLanguageParam.equals("no-language")?null:targetLanguageParam):null;
		
		SkosPlayConfig.getInstance().getSqlLogDao().insertLog(new LogEntry(
				language,
				outputParam,
				displayParam,
				skosPlayModel.getInputUrl(),
				"print",
				schemeParam
		));
		
		// read all potential languages and exclude the main one
		final List<String> additionalLanguages = new ArrayList<String>();
		for (String aLang : languages) {
			if(!aLang.equals(language)) {
				additionalLanguages.add(aLang);
			}
		}
		
		doPrint(
			r,
			outputType,
			displayType,
			language,
			SessionData.get(request.getSession()).getUserLocale().getLanguage(),
			additionalLanguages,
			scheme,
			targetLanguage,
			response			
		);



		response.flushBuffer();
	}
	

	@RequestMapping(value = "/print", method = RequestMethod.POST)
	public void print(
			// output type, PDF or HTML
			@RequestParam(value="output", required=true) String outputParam,
			@RequestParam(value="display", required=true) String displayParam,
			@RequestParam(value="language", defaultValue="no-language") String language,
			@RequestParam(value="scheme", defaultValue="no-scheme") String schemeParam,
			@RequestParam(value="targetLanguage", defaultValue="no-language") String targetLanguageParam,
			HttpServletRequest request,
			HttpServletResponse response
	) throws Exception {
		
		// get output type param
		OutputType outputType = (outputParam != null)?OutputType.valueOf(outputParam.toUpperCase()):null;

		// get display type param
		DisplayType displayType = (displayParam != null)?DisplayType.valueOf(displayParam.toUpperCase()):null;

		// get scheme param
		IRI scheme = (schemeParam.equals("no-scheme"))?null:SimpleValueFactory.getInstance().createIRI(schemeParam);

		// update source language param - only for translations
		language = (language.equals("no-language"))?null:language;

		// get target language param - only for translations
		String targetLanguage = (targetLanguageParam != null)?(targetLanguageParam.equals("no-language")?null:targetLanguageParam):null;
		
		SessionData sessionData=SessionData.get(request.getSession());
		// retrieve data from session
		Repository r = sessionData.getSkosPlayModel().getRepository();
		
		SkosPlayConfig.getInstance().getSqlLogDao().insertLog(new LogEntry(
				language,
				outputParam,
				displayParam,
				sessionData.getSkosPlayModel().getInputUrl(),
				"print",
				schemeParam
		));
		
		// read all potential languages and exclude the main one
		final List<String> additionalLanguages = new ArrayList<String>();
		for (String aLang : SessionData.get(request.getSession()).getPrintFormData().getLanguages().keySet()) {
			if(!aLang.equals(language)) {
				additionalLanguages.add(aLang);
			}
		}
		
		doPrint(
			r,
			outputType,
			displayType,
			language,
			SessionData.get(request.getSession()).getUserLocale().getLanguage(),
			additionalLanguages,
			scheme,
			targetLanguage,
			response			
		);



		response.flushBuffer();		
	}
	
	
	
	protected void doPrint(	
			Repository r,
			// output type, PDF or HTML
			OutputType outputType,
			DisplayType displayType,
			String language,
			String userLanguage,
			List<String> additionalLanguages,
			IRI scheme,
			String targetLanguage,
			HttpServletResponse response
	) throws Exception {

		
		// build display result
		KosDocument document = new KosDocument();
		
		try(RepositoryConnection connection = r.getConnection()) {
			// make a log to trace usage
			String aRandomConcept = Perform.on(connection).read(new SimpleQueryReader(this, "ReadRandomConcept.rq").get()).stringValue();
			log.info("PRINT,"+SimpleDateFormat.getDateTimeInstance().format(new Date())+","+scheme+","+aRandomConcept+","+language+","+displayType+","+outputType);
	
			HeaderAndFooterReader headerReader = new HeaderAndFooterReader(connection);
			headerReader.setApplicationString("Generated by SKOS Play!, sparna.fr");
			// on désactive complètement le header pour les PDF
			if(outputType != OutputType.PDF) {
				// build and set header
				document.setHeader(headerReader.readHeader(language, scheme));
			}
			// all the time, set footer
			document.setFooter(headerReader.readFooter(language, scheme));
	
			// pass on Repository to skos-printer level
			BodyReader bodyReader;
			
			
			switch(displayType) {
			case ALPHABETICAL : {			
				ConceptBlockReader cbr = new ConceptBlockReader();
				bodyReader = new BodyReader(new AlphaIndexDisplayGenerator(connection, cbr));			
				break;
			}
			case ALPHABETICAL_EXPANDED : {			
				ConceptBlockReader cbr = new ConceptBlockReader();
				cbr.setSkosPropertiesToRead(AlphaIndexDisplayGenerator.EXPANDED_SKOS_PROPERTIES_WITH_TOP_TERMS);
				bodyReader = new BodyReader(new AlphaIndexDisplayGenerator(connection, cbr));
				break;
			}
			case HIERARCHICAL : {
				bodyReader = new BodyReader(new HierarchicalDisplayGenerator(connection, new ConceptBlockReader()));
				break;
			}
			case HIERARCHICAL_TREE : {
				bodyReader = new BodyReader(new HierarchicalDisplayGenerator(connection, new ConceptBlockReader()));
				break;
			}
			//			case HIERARCHICAL_EXPANDED : {
			//				displayGenerator = new HierarchicalDisplayGenerator(r, new ConceptBlockReader(r, HierarchicalDisplayGenerator.EXPANDED_SKOS_PROPERTIES));
			//				break;
			//			}
			case CONCEPT_LISTING : {
				ConceptBlockReader cbr = new ConceptBlockReader();
				cbr.setSkosPropertiesToRead(ConceptListDisplayGenerator.EXPANDED_SKOS_PROPERTIES_WITH_TOP_TERMS);
				cbr.setAdditionalLabelLanguagesToInclude(additionalLanguages);
	
				bodyReader = new BodyReader(new ConceptListDisplayGenerator(connection, cbr));
				break;
			}
			case TRANSLATION_TABLE : {
				bodyReader = new BodyReader(new TranslationTableDisplayGenerator(connection, new ConceptBlockReader(), targetLanguage));
				break;
			}
			case PERMUTED_INDEX : {
				bodyReader = new BodyReader(new IndexGenerator(connection, IndexType.KWAC));
				break;
			}
			case KWIC_INDEX : {
				bodyReader = new BodyReader(new IndexGenerator(connection, IndexType.KWIC));
				break;
			}
			case COMPLETE_MONOLINGUAL : {
	
				// prepare a list of generators
				List<AbstractKosDisplayGenerator> generators = new ArrayList<AbstractKosDisplayGenerator>();
	
				// alphabetical display
				ConceptBlockReader alphaCbReader = new ConceptBlockReader();
				alphaCbReader.setStyleAttributes(true);
				alphaCbReader.setSkosPropertiesToRead(AlphaIndexDisplayGenerator.EXPANDED_SKOS_PROPERTIES_WITH_TOP_TERMS);
				alphaCbReader.setLinkDestinationIdPrefix("hier");
				AlphaIndexDisplayGenerator alphaGen = new AlphaIndexDisplayGenerator(
						connection,
						alphaCbReader,
						"alpha"
						);
				generators.add(alphaGen);
	
				// hierarchical display
				ConceptBlockReader hierCbReader = new ConceptBlockReader();
				hierCbReader.setLinkDestinationIdPrefix("alpha");
				HierarchicalDisplayGenerator hierarchyGen = new HierarchicalDisplayGenerator(
						connection,
						hierCbReader,
						"hier"
						);
				generators.add(hierarchyGen);
	
				bodyReader = new BodyReader(generators);				
	
				break;
			}
			case COMPLETE_MULTILINGUAL : {
	
				// prepare a list of generators
				List<AbstractKosDisplayGenerator> generators = new ArrayList<AbstractKosDisplayGenerator>();
	
				// alphabetical display
				ConceptBlockReader alphaCbReader = new ConceptBlockReader();
				alphaCbReader.setStyleAttributes(true);
				alphaCbReader.setSkosPropertiesToRead(AlphaIndexDisplayGenerator.EXPANDED_SKOS_PROPERTIES_WITH_TOP_TERMS);
				alphaCbReader.setAdditionalLabelLanguagesToInclude(additionalLanguages);
				alphaCbReader.setLinkDestinationIdPrefix("hier");
				AlphaIndexDisplayGenerator alphaGen = new AlphaIndexDisplayGenerator(
						connection,
						alphaCbReader,
						"alpha"
						);
				generators.add(alphaGen);
	
				// hierarchical display
				ConceptBlockReader hierCbReader = new ConceptBlockReader();
				hierCbReader.setLinkDestinationIdPrefix("alpha");
				HierarchicalDisplayGenerator hierarchyGen = new HierarchicalDisplayGenerator(
						connection,
						hierCbReader,
						"hier"
						);
				generators.add(hierarchyGen);
	
				// add translation tables for each additional languages
				for (int i=0;i<additionalLanguages.size(); i++) {
					String anAdditionalLang = additionalLanguages.get(i);
					ConceptBlockReader aCbReader = new ConceptBlockReader();
					aCbReader.setLinkDestinationIdPrefix("alpha");
					TranslationTableReverseDisplayGenerator ttGen = new TranslationTableReverseDisplayGenerator(
							connection,
							aCbReader,
							anAdditionalLang,
							"trans"+i);
					generators.add(ttGen);
				}
	
				bodyReader = new BodyReader(generators);
	
				break;
			}
			case ALIGNMENT_ALPHA : {
				AlignmentDataHarvesterIfc harvester = new AlignmentDataHarvesterCachedLoader(null, RDFFormat.RDFXML);
				AlignmentDisplayGenerator adg = new AlignmentDisplayGenerator(connection, new ConceptBlockReader(), harvester);
				// this is the difference with other alignment display
				adg.setSeparateByTargetScheme(false);
				bodyReader = new BodyReader(adg);
				break;
			}
			case ALIGNMENT_BY_SCHEME : {
				AlignmentDataHarvesterIfc harvester = new AlignmentDataHarvesterCachedLoader(null, RDFFormat.RDFXML);
				AlignmentDisplayGenerator adg = new AlignmentDisplayGenerator(connection, new ConceptBlockReader(), harvester);
				// this is the difference with other alignment display
				adg.setSeparateByTargetScheme(true);
				bodyReader = new BodyReader(adg);
				break;
			}
			default :
				throw new InvalidParameterException("Unknown display type "+displayType);
			}	
			// read the body
			document.setBody(bodyReader.readBody(language, scheme));
			}
			
			DisplayPrinter printer = new DisplayPrinter();
			// TODO : use Spring for configuration for easier debugging config
			// for the moment we desactivate debugging completely
			printer.setDebug(false);
			
			
			switch(outputType) {
			case HTML : {
				if(displayType==DisplayType.HIERARCHICAL_TREE) {
					printer.printToHtmlTree(document, response.getOutputStream(), userLanguage);
				} else {
					printer.printToHtml(document, response.getOutputStream(), userLanguage);
				}
				break;
			}
			case PDF : {
				response.setContentType("application/pdf");
				// if alphabetical or concept listing display, set 2-columns layout
				if(
						displayType == DisplayType.ALPHABETICAL
						||
						displayType == DisplayType.CONCEPT_LISTING
						||
						displayType == DisplayType.ALPHABETICAL_EXPANDED
						) {
					printer.getTransformerParams().put("column-count", 2);
				}
				printer.printToPdf(document, response.getOutputStream(), userLanguage);
				break;
			}
		}

		response.flushBuffer();	
	}
	

	protected String generateJSON (
			RepositoryConnection connection,
			String language,
			IRI scheme
	) throws Exception {

		// Careful : we need to use the same init code here than in the hierarhical display generator to get a consistent output
		PreferredPropertyReader ppr = new PreferredPropertyReader(
				connection,
				Arrays.asList(new IRI[] { SimpleValueFactory.getInstance().createIRI(SKOS.NOTATION), SimpleValueFactory.getInstance().createIRI(SKOS.PREF_LABEL) }),
				language
				);
		ppr.setCaching(true);

		TypeReader typeReader = new TypeReader();
		typeReader.setPreLoad(false);
		SKOSNodeTypeReader nodeTypeReader = new SKOSNodeTypeReader(typeReader, connection);

		SKOSTreeBuilder builder = new SKOSTreeBuilder(connection, new SKOSNodeSortCriteriaPreferredPropertyReader(ppr), nodeTypeReader);

		builder.setUseConceptSchemesAsFirstLevelNodes(false);

		GenericTree<SKOSTreeNode> tree = buildTree(builder, scheme);			

		// writes json output
		LabelReader labelReader = new LabelReader(connection, language);
		JsonSKOSTreePrinter printer = new JsonSKOSTreePrinter(labelReader);
		printer.setPrettyPrinting(false);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		printer.print(tree, baos);
		return baos.toString("UTF-8").replaceAll("'", "\\\\'");
	}

	public GenericTree<SKOSTreeNode> buildTree(SKOSTreeBuilder builder, IRI root) {
		GenericTree<SKOSTreeNode> tree = new GenericTree<SKOSTreeNode>();

		List<GenericTree<SKOSTreeNode>> trees;

		if(root != null) {	
			// generates tree
			log.debug("Building tree with root "+root);
			trees = builder.buildTrees(root);
		} else {
			// fetch all trees
			log.debug("Building tree with no particular root ");
			trees = builder.buildTrees();
		}

		// if only one, set it as root
		if(trees.size() == 1) {
			log.debug("Single tree found in the result");
			tree = trees.get(0);
		} else if (trees.size() ==0) {
			log.warn("Warning, no trees found");
		} else {
			log.debug("Multiple trees found ("+trees.size()+"), will create a fake root to group them all");
			// otherwise, create a fake root
			GenericTreeNode<SKOSTreeNode> fakeRoot = new GenericTreeNode<SKOSTreeNode>();
			fakeRoot.setData(new SKOSTreeNode(SimpleValueFactory.getInstance().createIRI("skosplay:allData"), "", NodeType.UNKNOWN));

			// add all the trees under it					
			for (GenericTree<SKOSTreeNode> genericTree : trees) {
				log.debug("Addind tree under fake root : "+genericTree.getRoot().getData().getIri());
				fakeRoot.addChild(genericTree.getRoot());
			}

			// set the root of the tree
			tree.setRoot(fakeRoot);
		}				

		return tree;
	}

}
