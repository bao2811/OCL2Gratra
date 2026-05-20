package run;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.emoflon.neo.api.familiestopersons.run.FamiliesToPersons_GEN_Run;
import org.emoflon.neo.emf.Neo4jImporter;
import org.emoflon.neo.neocore.ENeoUtil;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;

public class FamiliesToPersons_Import_Run {
	
	private static final Logger logger = Logger.getLogger(FamiliesToPersons_GEN_Run.class);

        /** arg0=FamiliesMM, arg1=PersonsMM, arg2=FamiliesModel, arg3=PersonsModel
        */
//	public static void main(String[] pArgs) throws Exception {
//		Logger.getRootLogger().setLevel(Level.INFO);
//		
//		ResourceSet rs = ENeoUtil.createEMSLStandaloneResourceSet(".");
//				
//		loadMetamodel(rs, "./src/metamodels/Families.ecore");
//		loadMetamodel(rs, "./src/metamodels/Persons.ecore");
//		loadMetamodel(rs, "./src/metamodels/IBeXTGGFamiliesToPersons.ecore");
////		rs.getResource(URI.createURI("./src/metamodels/IBeXTGGFamiliesToPersons.ecore"), true)
////		  .setURI(URI.createURI("FamiliesToPersons"));
//		
//		loadModel(rs, "./src/models/Families.xmi", "Families");
////		loadModel(rs, "./src/models/Persons.xmi", "Persons");
//		
////		new Neo4jImporter().importEMFModels(rs, "bolt://localhost:7687", "neo4j", "test");
//		new Neo4jImporter().importEMFModels(rs, "bolt://127.0.0.1:7687", "neo4j", "bao12345");
//
//	}
	
	public static void main(String[] pArgs) throws Exception {
	    Logger.getRootLogger().setLevel(Level.INFO);
	    ResourceSet rs = ENeoUtil.createEMSLStandaloneResourceSet(".");
	    
	    // 1. Nạp Metamodels bằng NS URI thực tế (Cực kỳ quan trọng)
	    // Đừng dùng setURI("FamiliesMM"), hãy để nó lấy NsURI từ file .ecore
	    loadMetamodel(rs, "./src/metamodels/Families.ecore");
	    loadMetamodel(rs, "./src/metamodels/Persons.ecore");
	    loadMetamodel(rs, "./src/metamodels/IBeXTGGFamiliesToPersons.ecore");
//	    loadModel(rs, "./src/models/IBeXTGGFamiliesToPersons.xmi", "CorrespondenceModel");
	    
	    // 2. Nạp Model dữ liệu và đặt nhãn duy nhất
	    Resource resModel = rs.getResource(URI.createURI("./src/models/Families.xmi"), true);
//	    resModel.setURI(URI.createURI("FamiliesToPersons_Source")); 
	    resModel.setURI(URI.createURI("FamiliesToPersons_Constrained_Source")); 
	    
	    
	    // 3. Thực hiện Import
	    new Neo4jImporter().importEMFModels(rs, "bolt://127.0.0.1:7687", "neo4j", "bao12345");
	    
	    System.out.println(">>> IMPORT THANH CONG VOI TEN GOC!");
	}
	
	private static void loadModel(ResourceSet rs, String uri, String label) {
		Resource resource = rs.getResource(URI.createURI(uri), true);
		resource.setURI(URI.createURI(label));
	}

	private static void loadMetamodel(ResourceSet rs, String uri) {
		var resource = rs.getResource(URI.createURI(uri), true);
		EPackage root = (EPackage) resource.getContents().get(0);
		resource.setURI(URI.createURI(root.getNsURI()));
	}
	
//	private static void loadMetamodel(ResourceSet rs, String path) {
//	    Resource resource = rs.getResource(URI.createURI(path), true);
//	    EPackage root = (EPackage) resource.getContents().get(0);
//	    
//	    // Đăng ký vào registry để EMF biết cách đọc các file XMI liên quan
//	    rs.getPackageRegistry().put(root.getNsURI(), root);
//	    
//	    // Ép URI của resource về tên đơn giản (ví dụ: "IBeXTGG") 
//	    // để Neo4j tạo node Metamodel với nhãn này
//	    resource.setURI(URI.createURI(root.getName())); 
//	    
//	    System.out.println("Registered Metamodel: " + root.getName() + " with URI: " + root.getNsURI());
//	}

}

