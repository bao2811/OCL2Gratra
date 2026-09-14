// OCL_val concrete syntax — ANTLR4 grammar, faithful to
// research/OCLscope/OCL-Concrete-Grammar.ebnf (OMG OCL 2.4 profile).
//
// Recognition/precedence only; admission (null/invalid/range/Sequence/…) and
// name/type resolution belong to semantic analysis (E_SM/N_SM), not here.
//
// Documented deviation from the EBNF: astral-plane NAME_START ranges
// (U+10000..U+EFFFF) are not encoded (Java UTF-16 char sets); identifiers
// outside the BMP need escaped form. Everything else follows §1–§6.
grammar OCL;

@parser::members {
    /** Iterator names are contextual (EBNF §6 note): ordinary WORDs that the
     *  parser matches by position, never global keywords. */
    private static boolean isIteratorName(String s) {
        return s.equals("select") || s.equals("reject") || s.equals("exists")
                || s.equals("forAll") || s.equals("collect");
    }
}

// ----------------------------------------------------------------------
// §1 Document and classifier invariant
// ----------------------------------------------------------------------
document
    : packageDeclaration EOF
    | classifierContextDeclaration* EOF
    ;

packageDeclaration
    : 'package' pathName classifierContextDeclaration* 'endpackage'
    ;

classifierContextDeclaration
    : 'context' classifierContextHead invariantDeclaration+
    ;

classifierContextHead
    : simpleName ':' pathName
    | pathName
    ;

invariantDeclaration
    : 'inv' simpleName? ':' expression
    ;

// ----------------------------------------------------------------------
// §2 Expression precedence (weakest first).
// implies is RIGHT-associative (EBNF: impliesExpression recurses on itself);
// every other infix level is left-associative.
// ----------------------------------------------------------------------
expression
    : impliesExpression
    ;

impliesExpression
    : xorExpression ( 'implies' impliesExpression )?
    ;

xorExpression
    : orExpression ( 'xor' orExpression )*
    ;

orExpression
    : andExpression ( 'or' andExpression )*
    ;

andExpression
    : equalityExpression ( 'and' equalityExpression )*
    ;

equalityExpression
    : relationalExpression ( equalityOperator relationalExpression )*
    ;

equalityOperator
    : '=' | '<>'
    ;

relationalExpression
    : additiveExpression ( relationalOperator additiveExpression )*
    ;

relationalOperator
    : '<' | '>' | '<=' | '>='
    ;

additiveExpression
    : multiplicativeExpression ( additiveOperator multiplicativeExpression )*
    ;

additiveOperator
    : '+' | '-'
    ;

multiplicativeExpression
    : unaryExpression ( multiplicativeOperator unaryExpression )*
    ;

multiplicativeOperator
    : '*' | '/' | contextualMult
    ;

// div/mod are contextual: the lexer emits IDENTIFIER, the parser matches by
// position. A single alternative with one predicate keeps adaptive prediction
// from trying div, failing, and losing the token before mod is considered.
contextualMult
    : IDENTIFIER { _localctx.start.getText().equals("div") || _localctx.start.getText().equals("mod") }?
    ;

unaryExpression
    : ( 'not' | '-' ) unaryExpression
    | postfixExpression
    ;

postfixExpression
    : primaryExpression postfixPart*
    ;

postfixPart
    : dotNavigation
    | arrowOperationCall
    | iteratorCall
    ;

dotNavigation
    : '.' simpleName ( '[' argumentList ']' | '(' argumentList? ')' )?
    ;

arrowOperationCall
    : '->' nonIteratorOperationName '(' argumentList? ')'
    ;

iteratorCall
    : '->' iteratorName '(' iteratorArguments ')'
    ;

iteratorName
    : IDENTIFIER { isIteratorName(_localctx.getText()) }?
    ;

nonIteratorOperationName
    : IDENTIFIER { !isIteratorName(_localctx.getText()) }?
    | ESCAPED_IDENTIFIER
    ;

iteratorArguments
    : ( iteratorDeclaration '|' )? expression
    ;

iteratorDeclaration
    : simpleName ( ':' type )?
    ;

argumentList
    : expression ( ',' expression )*
    ;

// ----------------------------------------------------------------------
// §3 Primary expressions
// ----------------------------------------------------------------------
primaryExpression
    : literalExpression
    | 'self'
    | implicitOperationCall
    | pathName
    | parenthesizedExpression
    | ifExpression
    | letExpression
    ;

implicitOperationCall
    : simpleName '(' argumentList? ')'
    ;

parenthesizedExpression
    : '(' expression ')'
    ;

ifExpression
    : 'if' expression 'then' expression 'else' expression 'endif'
    ;

letExpression
    : 'let' variableDeclaration 'in' expression
    ;

variableDeclaration
    : simpleName ( ':' type )? '=' expression
    ;

literalExpression
    : BOOLEAN_LITERAL
    | INTEGER_LITERAL
    | REAL_LITERAL
    | STRING_LITERAL
    | collectionLiteral
    | 'null'
    | 'invalid'
    ;

collectionLiteral
    : collectionKind '{' collectionLiteralParts? '}'
    ;

collectionKind
    : 'Set' | 'Bag'
    ;

collectionLiteralParts
    : collectionLiteralPart ( ',' collectionLiteralPart )*
    ;

// Ranges are parsed here but rejected by admission (EBNF §7).
collectionLiteralPart
    : expression ( '..' expression )?
    ;

// ----------------------------------------------------------------------
// §4 Type and name syntax
// ----------------------------------------------------------------------
type
    : primitiveType
    | collectionType
    | pathName
    ;

primitiveType
    : 'Boolean' | 'Integer' | 'Real' | 'String'
    ;

collectionType
    : collectionKind '(' scalarType ')'
    ;

scalarType
    : primitiveType | pathName
    ;

pathName
    : simpleName ( '::' unreservedSimpleName )*
    ;

simpleName
    : IDENTIFIER
    | ESCAPED_IDENTIFIER
    ;

unreservedSimpleName
    : simpleName
    | restrictedWord
    ;

restrictedWord
    : 'Bag' | 'Boolean' | 'Collection' | 'Integer'
    | 'OclAny' | 'OclInvalid' | 'OclMessage' | 'OclVoid'
    | 'OrderedSet' | 'Real' | 'Sequence' | 'Set'
    | 'String' | 'Tuple' | 'UnlimitedNatural'
    ;

// ----------------------------------------------------------------------
// §5 Lexical grammar
// ----------------------------------------------------------------------
BOOLEAN_LITERAL
    : 'true' | 'false'
    ;

INTEGER_LITERAL
    : '0' | [1-9] [0-9]*
    ;

REAL_LITERAL
    : [0-9]+ '.' [0-9]+ EXPONENT?
    | [0-9]+ EXPONENT
    ;

STRING_LITERAL
    : STRING_FRAGMENT ( STRING_GAP STRING_FRAGMENT )*
    ;

ESCAPED_IDENTIFIER
    : '_' STRING_FRAGMENT ( STRING_GAP STRING_FRAGMENT )*
    ;

IDENTIFIER
    : NAME_START NAME_CONTINUE*
    ;

fragment EXPONENT
    : [eE] [+-]? [0-9]+
    ;

fragment STRING_FRAGMENT
    : '\'' ( STRING_CHARACTER | ESCAPE_SEQUENCE )* '\''
    ;

fragment STRING_GAP
    : [ \t\r\n\f]+
    ;

fragment STRING_CHARACTER
    : ~['\\\r\n]
    ;

fragment ESCAPE_SEQUENCE
    : '\\' ( [btrfn"'\\/0] )
    | '\\x' HEX_DIGIT HEX_DIGIT
    | '\\u' HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT
    ;

fragment NAME_START
    : [A-Za-z_$]
    | 'À'..'Ö' | 'Ø'..'ö' | 'ø'..'˿'
    | 'Ͱ'..'ͽ' | 'Ϳ'..'῿'
    | '‌'..'‍' | '⁰'..'↏' | 'Ⰰ'..'⿯'
    | '、'..'퟿' | '豈'..'﷏' | 'ﷰ'..'�'
    ;

fragment NAME_CONTINUE
    : NAME_START | [0-9]
    ;

fragment HEX_DIGIT
    : [0-9A-Fa-f]
    ;

LINE_COMMENT
    : '--' ~[\r\n]* -> skip
    ;

WS
    : [ \t\r\n\f]+ -> skip
    ;
