package org.uet.dse.neo4j.incrementalUpdate;

import org.antlr.runtime.Token;
import org.tzi.use.parser.Context;
import org.tzi.use.parser.SemanticException;
import org.tzi.use.parser.ocl.ASTEnumTypeDefinition;
import org.tzi.use.parser.use.*;
import org.tzi.use.parser.use.statemachines.ASTSignal;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.mm.commonbehavior.communications.MSignal;
import org.tzi.use.uml.ocl.type.EnumType;
import org.uet.dse.neo4j.sync.model.sukunaDomainExpansion.FukumaMizushi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FakeASTModel extends ASTAnnotatable {
  private final ASTModel realAstModel;

  public FakeASTModel(ASTModel realAstModel) {
    this.realAstModel = realAstModel;
  }

  public MModel gen(Context ctx, FukumaMizushi fukumaMizushi) {
    MModel model = ctx.modelFactory().createModel(ctx.filename());
    model.setFilename("on merging by Kien");
    ctx.setModel(model);

    this.genAnnotations(model);

    // (1a) add user-defined types to model
    for (ASTEnumTypeDefinition e : realAstModel.getfEnumTypeDefs()) {
      EnumType enm;
      try {
        enm = e.gen(ctx);
        model.addEnumType(enm);
      } catch (SemanticException ex) {
        ctx.reportError(ex);
      } catch (MInvalidModelException ex) {
        ctx.reportError(realAstModel.getfName(), ex);
      }
    }

    // (1b) add empty data types to model
    Iterator<ASTDataType> dIt = realAstModel.getfDataTypes().iterator();
    while (dIt.hasNext()) {
      ASTDataType d = dIt.next();
      try {
        MDataType dtp = d.genEmptyDataType(ctx);
        model.addDataType(dtp);
      } catch (SemanticException ex) {
        ctx.reportError(ex);
        dIt.remove();
      } catch (MInvalidModelException ex) {
        ctx.reportError(realAstModel.getfName(), ex);
        dIt.remove();
      }
    }

    // (1b) add empty classes to model
    Iterator<ASTClass> cIt = realAstModel.getfClasses().iterator();
    while(cIt.hasNext()) {
      ASTClass c = cIt.next();

      try {
        MClass cls = c.genEmptyClass(ctx);
        model.addClass(cls);
      } catch (SemanticException ex) {
        ctx.reportError(ex);
        cIt.remove();
      } catch (MInvalidModelException ex) {
        ctx.reportError(realAstModel.getfName(), ex);
        cIt.remove();
      }
    }

    // (1c) add empty association classes to model
    Iterator<ASTAssociationClass> acIt = realAstModel.getfAssociationClasses().iterator();
    while ( acIt.hasNext() ) {
      ASTAssociationClass ac = acIt.next();
      try {
        // The association class can just be added as a class so far,
        // because to keep the order of generating a model.
        // The association class will be added as an association in step 3b.
        MAssociationClass assocCls = ac.genEmptyAssocClass( ctx );
        model.addClass( assocCls );
      } catch ( SemanticException ex ) {
        ctx.reportError( ex );
        acIt.remove();
      } catch ( MInvalidModelException ex ) {
        ctx.reportError(realAstModel.getfName(), ex );
        acIt.remove();
      }
    }

    // (1c) add empty signals to model
    {
      Iterator<ASTSignal> iter = this.realAstModel.getSignals().iterator();
      while (iter.hasNext()) {
        ASTSignal s = iter.next();

        try {
          MSignal signal = s.genEmptySignal(ctx);
          model.addSignal(signal);
        } catch (SemanticException ex) {
          ctx.reportError( ex );
          iter.remove();
        } catch (MInvalidModelException e1) {
          ctx.reportError( s.getName(), e1 );
          iter.remove();
        }
      }
    }

    // (2a) add attributes and set generalization
    // relationships. The names of all data types are known at this
    // point
    for (ASTDataType d : realAstModel.getfDataTypes()) {
      d.genAttributesOperationSignaturesAndGenSpec(ctx);
    }

    // (2a) add attributes and set generalization
    // relationships. The names of all classes are known at this
    // point
    for (ASTClass c : realAstModel.getfClasses()) {
      c.genAttributesOperationSignaturesAndGenSpec(ctx);
    }

    // (2b) add attributes and set generalization
    // relationships of the association classes.
    // The names of all classes are known at this point
    for (ASTAssociationClass ac : realAstModel.getfAssociationClasses()) {
      ac.genAttributesOperationSignaturesAndGenSpec( ctx );
    }

    // (2c) add attributes and set generalization relationships
    // of signals
    for (ASTSignal s : realAstModel.getSignals()) {
      s.genAttributesAndGenSpec( ctx );
    }

    // (3a) add associations. Classes are known and can be
    // referenced by role names.
    for (ASTAssociation a : realAstModel.getfAssociations()) {
      try {
        FakeASTAssociation fA = new FakeASTAssociation(a);
        fA.genG(ctx, model, fukumaMizushi);
      } catch (SemanticException ex) {
        ctx.reportError(ex);
      }
    }

    for (ASTClass c : realAstModel.getfClasses()) {
      c.genStateMachinesAndStates(ctx);
    }

    for (ASTAssociationClass ac : realAstModel.getfAssociationClasses()) {
      ac.genStateMachinesAndStates(ctx);
    }

    // (3b) add association classes as associations.
    // Classes are known and can be referenced by role names.
    for (ASTAssociationClass ac : realAstModel.getfAssociationClasses()) {
      try {
        // The association class is now added as an association.
        // It is added here to keep the order of generating a model.
        // The association class is already added as a class in step 1c.
        MAssociationClass assocClass = ac.genAssociation( ctx );
        model.addAssociation( assocClass );
      } catch ( SemanticException ex ) {
        ctx.reportError( ex );
      } catch ( MInvalidModelException ex ) {
        ctx.reportError(realAstModel.getfName(), ex );
      }
    }

    // (3c) Generalization of association classes might leave out new
    // rolenames. Add them from parent.
    for (ASTAssociationClass ac : realAstModel.getfAssociationClasses()) {
      try {
        ac.genAssociationFinal( ctx );
      } catch ( MInvalidModelException ex ) {
        ctx.reportError(realAstModel.getfName(), ex );
      }
    }


    // (3c) add associationEnd specific constraints, e. g. subsets
    // Role names are known and can be subset
    for (ASTAssociation a : realAstModel.getfAssociations()) {
      try {
        a.genEndConstraints(ctx);
      } catch (SemanticException ex) {
        ctx.reportError(ex);
      }
    }

    // (3c) add associationEnd specific constraints, e. g. subsets
    // Role names are known and can be subset
    for (ASTAssociationClass a : realAstModel.getfAssociationClasses()) {
      try {
        a.genEndConstraints(ctx);
      } catch (SemanticException ex) {
        ctx.reportError(ex);
      }
    }

    // (4a) generate bodies of data types
    for (ASTDataType d : realAstModel.getfDataTypes()) {
      d.genOperationBodiesAndDerivedAttributes(ctx);
    }

    // (4a) generate bodies of association and non-association classes
    // All class interfaces are known and association features
    // are available for expressions.
    for (ASTClass c : realAstModel.getfClasses()) {
      c.genOperationBodiesAndDerivedAttributes(ctx);
    }

    for (ASTAssociationClass ac : realAstModel.getfAssociationClasses()) {
      ac.genOperationBodiesAndDerivedAttributes(ctx);
    }

    // (4b) generate constraints of association and non-association data types
    // All data type interfaces are known and association features
    // are available for expressions.
    for (ASTDataType d : realAstModel.getfDataTypes()) {
      d.genConstraints(ctx);
    }

    // (4b) generate constraints of association and non-association classes
    // All class interfaces are known and association features
    // are available for expressions.
    for (ASTClass c : realAstModel.getfClasses()) {
      c.genConstraints(ctx);
    }

    for (ASTAssociationClass ac : realAstModel.getfAssociationClasses()) {
      ac.genConstraints(ctx);
    }

    // (5a) generate global constraints. All class interfaces are
    // known and association features are available for
    // expressions.
    for (ASTConstraintDefinition c : realAstModel.getfConstraints()) {
      c.gen(ctx);
    }

    /**
    // (5b) generate pre-/postconditions.
    for (ASTPrePost ppc : fPrePosts) {
      try {
        ppc.gen(ctx);
      } catch (SemanticException ex) {
        ctx.reportError(ex);
      }
    }
     */

    // Gen transitions
    for (ASTClass c : realAstModel.getfClasses()) {
      c.genStateMachineTransitions(ctx);
    }

    for (ASTAssociationClass ac : realAstModel.getfAssociationClasses()) {
      ac.genStateMachineTransitions(ctx);
    }

    return model;
  }
}
