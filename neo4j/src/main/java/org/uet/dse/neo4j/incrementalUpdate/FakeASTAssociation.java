package org.uet.dse.neo4j.incrementalUpdate;

import org.antlr.runtime.Token;
import org.tzi.use.parser.Context;
import org.tzi.use.parser.SemanticException;
import org.tzi.use.parser.use.ASTAssociation;
import org.tzi.use.parser.use.ASTAssociationEnd;
import org.tzi.use.uml.mm.*;
import org.uet.dse.neo4j.sync.model.sukunaDomainExpansion.FukumaMizushi;

import java.util.ArrayList;
import java.util.List;

public class FakeASTAssociation extends ASTAssociation {
  private final ASTAssociation realAstAssociation;
  private Token fKind;

  private Token fName;

  private List<ASTAssociationEnd> fAssociationEnds;

  public FakeASTAssociation(ASTAssociation realAstAssociation) {
    super(realAstAssociation.getfName(), realAstAssociation.getfName());
    this.realAstAssociation = realAstAssociation;
    fKind = realAstAssociation.getfKind();
    fName = realAstAssociation.getfName();
    fAssociationEnds = new ArrayList<ASTAssociationEnd>();
    realAstAssociation.getfAssociationEnds().forEach(e -> addEnd(e));
  }

  public void addEnd(ASTAssociationEnd ae) {
    fAssociationEnds.add(ae);
  }

  public MAssociation genG(Context ctx, MModel model, FukumaMizushi fukumaMizushi) throws SemanticException
  {
    checkDerive();

    MAssociation assoc = ctx.modelFactory().createAssociation(fName.getText());
    this.genAnnotations(assoc);

    // sets the line position of the USE-Model in this association
    assoc.setPositionInModel( fName.getLine() );
    String kindname = fKind.getText();
    int kind = MAggregationKind.NONE;

    if (kindname.equals("aggregation") )
      kind = MAggregationKind.AGGREGATION;
    else if (kindname.equals("composition") )
      kind = MAggregationKind.COMPOSITION;

    try {
      for (ASTAssociationEnd ae : fAssociationEnds) {
        if (!ae.getQualifiers().isEmpty() && this.fAssociationEnds.size() > 2) {
          throw new SemanticException(fName,
              "Error in " + MAggregationKind.name(assoc.aggregationKind()) + " `" +
                  assoc.name() + "': Only binary associations can be qualified.");
        }
        // kind of association determines kind of first
        // association end
        FakeASTAssociationEnd fAe = new FakeASTAssociationEnd(ae);
        MAssociationEnd aend = fAe.genG(ctx, kind, fukumaMizushi);
        assoc.addAssociationEnd(aend);

        // further ends are plain ends
        kind = MAggregationKind.NONE;

        if (aend.isUnion())
          assoc.setUnion(true);
      }
      model.addAssociation(assoc);
    } catch (MInvalidModelException ex) {
      throw new SemanticException(fName,
          "In " + MAggregationKind.name(assoc.aggregationKind()) + " `" +
              assoc.name() + "': " +
              ex.getMessage());
    }
    return assoc;
  }


  private void checkDerive() throws SemanticException {
    int derived = 0;
    for (ASTAssociationEnd aend : fAssociationEnds) {
      if (aend.isDerived()) derived++;
    }

    if ( derived > 1 ) {
      throw new SemanticException(fName, "Only one association end can be derived. One direction is always calculated by USE.");
    }
  }


  public String toString() {
    return "(" + fName + ", " + fKind.getText() + ")";
  }

}

