/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.ballerinalang.compiler.desugar;

import org.ballerinalang.compiler.CompilerPhase;
import org.ballerinalang.model.TreeBuilder;
import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.OperatorKind;
import org.ballerinalang.model.tree.clauses.JoinStreamingInput;
import org.ballerinalang.model.tree.expressions.NamedArgNode;
import org.ballerinalang.model.tree.statements.StatementNode;
import org.ballerinalang.model.tree.statements.StreamingQueryStatementNode;
import org.wso2.ballerinalang.compiler.PackageCache;
import org.wso2.ballerinalang.compiler.semantics.analyzer.SymbolResolver;
import org.wso2.ballerinalang.compiler.semantics.analyzer.Types;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolEnv;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BConversionOperatorSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BEndpointVarSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BInvokableSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BOperatorSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BPackageSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BStructSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BVarSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BXMLNSSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.SymTag;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.Symbols;
import org.wso2.ballerinalang.compiler.semantics.model.types.BArrayType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BInvokableType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BStructType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BTableType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BUnionType;
import org.wso2.ballerinalang.compiler.tree.BLangAction;
import org.wso2.ballerinalang.compiler.tree.BLangConnector;
import org.wso2.ballerinalang.compiler.tree.BLangEndpoint;
import org.wso2.ballerinalang.compiler.tree.BLangFunction;
import org.wso2.ballerinalang.compiler.tree.BLangIdentifier;
import org.wso2.ballerinalang.compiler.tree.BLangImportPackage;
import org.wso2.ballerinalang.compiler.tree.BLangInvokableNode;
import org.wso2.ballerinalang.compiler.tree.BLangNode;
import org.wso2.ballerinalang.compiler.tree.BLangNodeVisitor;
import org.wso2.ballerinalang.compiler.tree.BLangObject;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.tree.BLangResource;
import org.wso2.ballerinalang.compiler.tree.BLangService;
import org.wso2.ballerinalang.compiler.tree.BLangStruct;
import org.wso2.ballerinalang.compiler.tree.BLangTransformer;
import org.wso2.ballerinalang.compiler.tree.BLangVariable;
import org.wso2.ballerinalang.compiler.tree.BLangWorker;
import org.wso2.ballerinalang.compiler.tree.BLangXMLNS;
import org.wso2.ballerinalang.compiler.tree.BLangXMLNS.BLangLocalXMLNS;
import org.wso2.ballerinalang.compiler.tree.BLangXMLNS.BLangPackageXMLNS;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangArrayLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangArrayLiteral.BLangJSONArrayLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangAwaitExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangBinaryExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangBracedOrTupleExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangFieldBasedAccess;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangFieldBasedAccess.BLangEnumeratorAccessExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangFieldBasedAccess.BLangStructFieldAccessExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangIndexBasedAccess;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangIndexBasedAccess.BLangArrayAccessExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangIndexBasedAccess.BLangJSONAccessExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangIndexBasedAccess.BLangMapAccessExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangIndexBasedAccess.BLangXMLAccessExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangIntRangeExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangInvocation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangInvocation.BFunctionPointerInvocation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangInvocation.BLangActionInvocation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangInvocation.BLangAttachedFunctionInvocation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangInvocation.BLangTransformerInvocation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangIsAssignableExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLambdaFunction;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangNamedArgsExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral.BLangJSONLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral.BLangMapLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral.BLangStreamLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral.BLangStructLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral.BLangTableLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRestArgsExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef.BLangFieldVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef.BLangFunctionVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef.BLangLocalVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef.BLangPackageVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangStringTemplateLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTableQueryExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTernaryExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeCastExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeConversionExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeInit;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeofExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangUnaryExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangVariableReference;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLAttribute;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLAttributeAccess;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLCommentLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLElementLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLProcInsLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLQName;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLQuotedString;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLSequenceLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLTextLiteral;
import org.wso2.ballerinalang.compiler.tree.statements.BLangAbort;
import org.wso2.ballerinalang.compiler.tree.statements.BLangAssignment;
import org.wso2.ballerinalang.compiler.tree.statements.BLangBind;
import org.wso2.ballerinalang.compiler.tree.statements.BLangBlockStmt;
import org.wso2.ballerinalang.compiler.tree.statements.BLangBreak;
import org.wso2.ballerinalang.compiler.tree.statements.BLangCatch;
import org.wso2.ballerinalang.compiler.tree.statements.BLangCompoundAssignment;
import org.wso2.ballerinalang.compiler.tree.statements.BLangExpressionStmt;
import org.wso2.ballerinalang.compiler.tree.statements.BLangFail;
import org.wso2.ballerinalang.compiler.tree.statements.BLangForeach;
import org.wso2.ballerinalang.compiler.tree.statements.BLangForkJoin;
import org.wso2.ballerinalang.compiler.tree.statements.BLangIf;
import org.wso2.ballerinalang.compiler.tree.statements.BLangLock;
import org.wso2.ballerinalang.compiler.tree.statements.BLangMatch;
import org.wso2.ballerinalang.compiler.tree.statements.BLangMatch.BLangMatchStmtPatternClause;
import org.wso2.ballerinalang.compiler.tree.statements.BLangNext;
import org.wso2.ballerinalang.compiler.tree.statements.BLangPostIncrement;
import org.wso2.ballerinalang.compiler.tree.statements.BLangReturn;
import org.wso2.ballerinalang.compiler.tree.statements.BLangStatement;
import org.wso2.ballerinalang.compiler.tree.statements.BLangStatement.BLangStatementLink;
import org.wso2.ballerinalang.compiler.tree.statements.BLangThrow;
import org.wso2.ballerinalang.compiler.tree.statements.BLangTransaction;
import org.wso2.ballerinalang.compiler.tree.statements.BLangTryCatchFinally;
import org.wso2.ballerinalang.compiler.tree.statements.BLangTupleDestructure;
import org.wso2.ballerinalang.compiler.tree.statements.BLangVariableDef;
import org.wso2.ballerinalang.compiler.tree.statements.BLangWhenever;
import org.wso2.ballerinalang.compiler.tree.statements.BLangWhile;
import org.wso2.ballerinalang.compiler.tree.statements.BLangWorkerReceive;
import org.wso2.ballerinalang.compiler.tree.statements.BLangWorkerSend;
import org.wso2.ballerinalang.compiler.tree.statements.BLangXMLNSStatement;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.Name;
import org.wso2.ballerinalang.compiler.util.Names;
import org.wso2.ballerinalang.compiler.util.TypeTags;
import org.wso2.ballerinalang.compiler.util.diagnotic.DiagnosticPos;
import org.wso2.ballerinalang.programfile.InstructionCodes;
import org.wso2.ballerinalang.util.Lists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import static org.wso2.ballerinalang.compiler.util.Names.GEN_VAR_PREFIX;

/**
 * @since 0.94
 */
public class Desugar extends BLangNodeVisitor {

    private static final CompilerContext.Key<Desugar> DESUGAR_KEY =
            new CompilerContext.Key<>();
    private static final String QUERY_TABLE_WITH_JOIN_CLAUSE = "queryTableWithJoinClause";
    private static final String QUERY_TABLE_WITHOUT_JOIN_CLAUSE = "queryTableWithoutJoinClause";
    private static final String CREATE_WHENEVER = "startWhenever";

    private SymbolTable symTable;
    private final PackageCache packageCache;
    private SymbolResolver symResolver;
    private IterableCodeDesugar iterableCodeDesugar;
    private AnnotationDesugar annotationDesugar;
    private EndpointDesugar endpointDesugar;
    private SqlQueryBuilder sqlQueryBuilder;
    private Types types;
    private Names names;
    private SiddhiQueryBuilder siddhiQueryBuilder;

    private BLangNode result;

    private BLangStatementLink currentLink;
    private Stack<BLangWorker> workerStack = new Stack<>();

    public Stack<BLangLock> enclLocks = new Stack<>();

    private SymbolEnv env;

    public static Desugar getInstance(CompilerContext context) {
        Desugar desugar = context.get(DESUGAR_KEY);
        if (desugar == null) {
            desugar = new Desugar(context);
        }

        return desugar;
    }

    private Desugar(CompilerContext context) {
        context.put(DESUGAR_KEY, this);
        this.symTable = SymbolTable.getInstance(context);
        this.symResolver = SymbolResolver.getInstance(context);
        this.iterableCodeDesugar = IterableCodeDesugar.getInstance(context);
        this.annotationDesugar = AnnotationDesugar.getInstance(context);
        this.endpointDesugar = EndpointDesugar.getInstance(context);
        this.sqlQueryBuilder = SqlQueryBuilder.getInstance(context);
        this.types = Types.getInstance(context);
        this.names = Names.getInstance(context);
        this.siddhiQueryBuilder = SiddhiQueryBuilder.getInstance(context);
        this.packageCache = PackageCache.getInstance(context);
        this.names = Names.getInstance(context);
    }

    public BLangPackage perform(BLangPackage pkgNode) {
        return rewrite(pkgNode, env);
    }

    // visitors

    @Override
    public void visit(BLangPackage pkgNode) {
        if (pkgNode.completedPhases.contains(CompilerPhase.DESUGAR)) {
            result = pkgNode;
            return;
        }
        SymbolEnv env = this.symTable.pkgEnvMap.get(pkgNode.symbol);

        //Adding object functions to package level.
        pkgNode.objects.forEach(o -> {
            o.functions.forEach(f -> {
                pkgNode.functions.add(f);
                pkgNode.topLevelNodes.add(f);
            });
        });
        //Rewriting Object to struct
        pkgNode.objects.forEach(o -> pkgNode.structs.add(rewriteObjectToStruct(o, env)));

        pkgNode.structs = rewrite(pkgNode.structs, env);
        // Adding struct init functions to package level.
        pkgNode.structs.forEach(struct -> {
            pkgNode.functions.add(struct.initFunction);
            pkgNode.topLevelNodes.add(struct.initFunction);
        });

        pkgNode.imports = rewrite(pkgNode.imports, env);
        pkgNode.xmlnsList = rewrite(pkgNode.xmlnsList, env);
        pkgNode.globalVars = rewrite(pkgNode.globalVars, env);
        pkgNode.globalEndpoints = rewrite(pkgNode.globalEndpoints, env);
        pkgNode.globalEndpoints.forEach(endpoint -> endpointDesugar.defineGlobalEndpoint(endpoint, env));
        annotationDesugar.rewritePackageAnnotations(pkgNode);
        endpointDesugar.rewriteAllEndpointsInPkg(pkgNode, env);
        endpointDesugar.rewriteServiceBoundToEndpointInPkg(pkgNode, env);
        pkgNode.transformers = rewrite(pkgNode.transformers, env);
        pkgNode.functions = rewrite(pkgNode.functions, env);
        pkgNode.connectors = rewrite(pkgNode.connectors, env);
        pkgNode.services = rewrite(pkgNode.services, env);
        pkgNode.initFunction = rewrite(pkgNode.initFunction, env);
        pkgNode.startFunction = rewrite(pkgNode.startFunction, env);
        pkgNode.stopFunction = rewrite(pkgNode.stopFunction, env);
        pkgNode.completedPhases.add(CompilerPhase.DESUGAR);
        result = pkgNode;
    }

    @Override
    public void visit(BLangImportPackage importPkgNode) {
        BPackageSymbol pkgSymbol = importPkgNode.symbol;
        SymbolEnv pkgEnv = this.symTable.pkgEnvMap.get(pkgSymbol);
        rewrite(pkgEnv.node, pkgEnv);
        result = importPkgNode;
    }

    @Override
    public void visit(BLangStruct structNode) {
        // Add struct level variables to the init function.
        structNode.fields.stream()
            .map(field -> {
                    // If the rhs value is not given in-line inside the struct
                    // then get the default value literal for that particular struct.
                    if (field.expr == null) {
                        field.expr = getInitExpr(field.type);
                    }
                    return field;
                })
            .filter(field -> field.expr != null)
            .forEachOrdered(field -> {
                if (!structNode.initFunction.initFunctionStmts.containsKey(field.symbol)) {
                    structNode.initFunction.initFunctionStmts.put(field.symbol,
                            (BLangStatement) createAssignmentStmt(field));
                }
            });

        //Adding init statements to the init function.
        BLangStatement[] initStmts = structNode.initFunction.initFunctionStmts.values().toArray(new BLangStatement[0]);
        for (int i = 0; i < structNode.initFunction.initFunctionStmts.size(); i++) {
            structNode.initFunction.body.stmts.add(i, initStmts[i]);
        }

        result = structNode;
    }

    @Override
    public void visit(BLangFunction funcNode) {
        SymbolEnv fucEnv = SymbolEnv.createFunctionEnv(funcNode, funcNode.symbol.scope, env);
        if (!funcNode.interfaceFunction) {
            addReturnIfNotPresent(funcNode);
        }

        Collections.reverse(funcNode.endpoints); // To preserve endpoint code gen order.
        funcNode.endpoints = rewrite(funcNode.endpoints, fucEnv);

        funcNode.body = rewrite(funcNode.body, fucEnv);
        funcNode.workers = rewrite(funcNode.workers, fucEnv);

        // If the function has a receiver, we rewrite it's parameter list to have
        // the struct variable as the first parameter
        if (funcNode.receiver != null) {
            BInvokableSymbol funcSymbol = funcNode.symbol;
            List<BVarSymbol> params = funcSymbol.params;
            params.add(0, funcNode.receiver.symbol);
            BInvokableType funcType = (BInvokableType) funcSymbol.type;
            funcType.paramTypes.add(0, funcNode.receiver.type);
        }

        result = funcNode;
    }

    @Override
    public void visit(BLangService serviceNode) {
        SymbolEnv serviceEnv = SymbolEnv.createServiceEnv(serviceNode, serviceNode.symbol.scope, env);
        serviceNode.resources = rewrite(serviceNode.resources, serviceEnv);
        serviceNode.vars = rewrite(serviceNode.vars, serviceEnv);
        serviceNode.endpoints = rewrite(serviceNode.endpoints, serviceEnv);
        serviceNode.initFunction = rewrite(serviceNode.initFunction, serviceEnv);
        result = serviceNode;
    }

    public void visit(BLangWhenever wheneverStatement) {
        siddhiQueryBuilder.visit(wheneverStatement);
        BLangExpressionStmt stmt = (BLangExpressionStmt) TreeBuilder.createExpressionStatementNode();
        stmt.expr = createInvocationForWheneverBlock(wheneverStatement);
        stmt.pos = wheneverStatement.pos;
        stmt.addWS(wheneverStatement.getWS());
        result = rewrite(stmt, env);
    }

    private BLangStruct rewriteObjectToStruct(BLangObject objectNode, SymbolEnv env) {
        BLangStruct bLangStruct = (BLangStruct) TreeBuilder.createStructNode();
        bLangStruct.name = objectNode.name;
        bLangStruct.fields = objectNode.fields;
//        bLangStruct.functions = rewrite(objectNode.functions, env);
        bLangStruct.initFunction = objectNode.initFunction;
        bLangStruct.annAttachments = rewrite(objectNode.annAttachments, env);
        bLangStruct.docAttachments = rewrite(objectNode.docAttachments, env);
        bLangStruct.deprecatedAttachments = rewrite(objectNode.deprecatedAttachments, env);
        bLangStruct.isAnonymous = objectNode.isAnonymous;
        bLangStruct.symbol = objectNode.symbol;

        return bLangStruct;
    }

    @Override
    public void visit(BLangResource resourceNode) {
        addReturnIfNotPresent(resourceNode);
        SymbolEnv resourceEnv = SymbolEnv.createResourceActionSymbolEnv(resourceNode, resourceNode.symbol.scope, env);
        Collections.reverse(resourceNode.endpoints); // To preserve endpoint code gen order at resource
        resourceNode.endpoints = rewrite(resourceNode.endpoints, resourceEnv);
        resourceNode.body = rewrite(resourceNode.body, resourceEnv);
        resourceNode.workers = rewrite(resourceNode.workers, resourceEnv);
        result = resourceNode;
    }

    @Override
    public void visit(BLangConnector connectorNode) {
        SymbolEnv conEnv = SymbolEnv.createConnectorEnv(connectorNode, connectorNode.symbol.scope, env);
        connectorNode.params = rewrite(connectorNode.params, conEnv);
        connectorNode.actions = rewrite(connectorNode.actions, conEnv);
        connectorNode.varDefs = rewrite(connectorNode.varDefs, conEnv);
        connectorNode.endpoints = rewrite(connectorNode.endpoints, conEnv);
        connectorNode.initFunction = rewrite(connectorNode.initFunction, conEnv);
        connectorNode.initAction = rewrite(connectorNode.initAction, conEnv);
        result = connectorNode;
    }

    @Override
    public void visit(BLangAction actionNode) {
        addReturnIfNotPresent(actionNode);
        SymbolEnv actionEnv = SymbolEnv.createResourceActionSymbolEnv(actionNode, actionNode.symbol.scope, env);
        Collections.reverse(actionNode.endpoints); // To preserve endpoint code gen order at action.
        actionNode.endpoints = rewrite(actionNode.endpoints, actionEnv);
        actionNode.body = rewrite(actionNode.body, actionEnv);
        actionNode.workers = rewrite(actionNode.workers, actionEnv);

        // we rewrite it's parameter list to have the receiver variable as the first parameter
        BInvokableSymbol actionSymbol = actionNode.symbol;
        List<BVarSymbol> params = actionSymbol.params;
        BVarSymbol receiverSymbol = actionNode.symbol.receiverSymbol;
        params.add(0, receiverSymbol);
        BInvokableType actionType = (BInvokableType) actionSymbol.type;
        if (receiverSymbol != null) {
            actionType.paramTypes.add(0, receiverSymbol.type);
        }
        result = actionNode;
    }

    @Override
    public void visit(BLangWorker workerNode) {
        this.workerStack.push(workerNode);
        workerNode.body = rewrite(workerNode.body, env);
        this.workerStack.pop();
        result = workerNode;
    }

    @Override
    public void visit(BLangEndpoint endpoint) {
        result = endpoint;
    }

    @Override
    public void visit(BLangVariable varNode) {
        if ((varNode.symbol.owner.tag & SymTag.INVOKABLE) != SymTag.INVOKABLE) {
            varNode.expr = null;
            result = varNode;
            return;
        }

        // Return if this assignment is not a safe assignment
        varNode.expr = rewriteExpr(varNode.expr);
        result = varNode;

    }

    public void visit(BLangTransformer transformerNode) {
        addTransformerReturn(transformerNode);
        SymbolEnv tranEnv = SymbolEnv.createTransformerEnv(transformerNode, transformerNode.symbol.scope, env);
        transformerNode.body = rewrite(transformerNode.body, tranEnv);

        addArgInitExpr(transformerNode, transformerNode.retParams.get(0));

        BInvokableSymbol transformerSymbol = transformerNode.symbol;
        List<BVarSymbol> params = transformerSymbol.params;
        params.add(0, transformerNode.source.symbol);
        BInvokableType transformerType = (BInvokableType) transformerSymbol.type;
        transformerType.paramTypes.add(0, transformerNode.source.type);

        result = transformerNode;
    }

    // Statements

    @Override
    public void visit(BLangBlockStmt block) {
        SymbolEnv blockEnv = SymbolEnv.createBlockEnv(block, env);
        block.stmts = rewriteStmt(block.stmts, blockEnv);
        result = block;
    }

    @Override
    public void visit(BLangVariableDef varDefNode) {
        if (varDefNode.var.expr instanceof BLangRecordLiteral &&
                ((BLangRecordLiteral) varDefNode.var.expr).type.tag == TypeTags.STREAM) {
            ((BLangRecordLiteral) varDefNode.var.expr).name = varDefNode.var.name;
        }

        varDefNode.var = rewrite(varDefNode.var, env);
        BLangVariable varNode = varDefNode.var;

        // Generate default init expression, if rhs expr is null
        if (varNode.expr == null) {
            varNode.expr = getInitExpr(varNode.type);
        }

        if (!varNode.safeAssignment) {
            result = varDefNode;
            return;
        }

        // Desugar the =? operator with the match statement
        //
        //  e.g.
        //      var f =? openFile("/tmp/foo.txt"); // openFile: () -> (File | error)
        //
        //      {
        //          File f;
        //          match openFile("/tmp/foo.txt") {
        //              File _$_f1 => f = _$_f1;
        //              error e => throw e | return e
        //          }
        //      }

        // Create the pattern to match the success case
        BLangMatchStmtPatternClause patternSuccessCase = getSafeAssignSuccessPattern(varNode.pos,
                varNode.symbol.type, true, varNode.symbol, null);
        BLangMatchStmtPatternClause patternErrorCase = getSafeAssignErrorPattern(varNode.pos, varNode.symbol.owner);


        // Create the match statement
        BLangMatch matchStmt = ASTBuilderUtil.createMatchStatement(varNode.expr.pos,
                varNode.expr, new ArrayList<BLangMatchStmtPatternClause>() {{
                    add(patternSuccessCase);
                    add(patternErrorCase);
                }});

        // var f =? foo() -> var f;
        varNode.expr = null;
        varNode.safeAssignment = false;

        BLangBlockStmt safeAssignmentBlock = ASTBuilderUtil.createBlockStmt(varDefNode.pos,
                new ArrayList<BLangStatement>() {{
                    add(varDefNode);
                    add(matchStmt);
                }});

        result = rewrite(safeAssignmentBlock, this.env);
    }

    @Override
    public void visit(BLangAssignment assignNode) {
        if (assignNode.expr.type.tag == TypeTags.STREAM && assignNode.varRefs.get(0) instanceof BLangSimpleVarRef) {
                ((BLangRecordLiteral) assignNode.expr).name =
                        ((BLangSimpleVarRef) assignNode.varRefs.get(0)).variableName;
        }
        assignNode.varRefs = rewriteExprs(assignNode.varRefs);
        assignNode.expr = rewriteExpr(assignNode.expr);
        result = assignNode;

        if (!assignNode.safeAssignment) {
            return;
        }

        // Desugar the =? operator with the match statement
        //
        //  e.g.
        //      File f;
        //      .....
        //      f =? openFile("/tmp/foo.txt"); // openFile: () -> (File | error)
        //
        //      {
        //          match openFile("/tmp/foo.txt") {
        //              File _$_f1 => f = _$_f1;
        //              error e => throw e | return e
        //          }
        //      }
        BLangBlockStmt safeAssignmentBlock = ASTBuilderUtil.createBlockStmt(assignNode.pos, new ArrayList<>());
        BLangExpression lhsExpr = assignNode.varRefs.get(0);
        BLangMatchStmtPatternClause patternSuccessCase;
        if (assignNode.declaredWithVar) {
            BVarSymbol varSymbol = ((BLangSimpleVarRef) lhsExpr).symbol;
            BLangVariable variable = ASTBuilderUtil.createVariable(assignNode.pos, "", lhsExpr.type, null, varSymbol);
            BLangVariableDef variableDef = ASTBuilderUtil.createVariableDef(assignNode.pos, variable);
            safeAssignmentBlock.stmts.add(variableDef);
            patternSuccessCase = getSafeAssignSuccessPattern(assignNode.pos, lhsExpr.type,
                    true, varSymbol, null);
        } else {
            patternSuccessCase = getSafeAssignSuccessPattern(assignNode.pos, lhsExpr.type,
                    false, null, lhsExpr);
        }

        // Create the pattern to match the success case
        BLangMatchStmtPatternClause patternErrorCase = getSafeAssignErrorPattern(assignNode.pos,
                this.env.enclInvokable.symbol);


        // Create the match statement
        BLangMatch matchStmt = ASTBuilderUtil.createMatchStatement(assignNode.pos,
                assignNode.expr, new ArrayList<BLangMatchStmtPatternClause>() {{
                    add(patternSuccessCase);
                    add(patternErrorCase);
                }});

        // var f =? foo() -> var f;
        assignNode.expr = null;
        assignNode.safeAssignment = false;
        safeAssignmentBlock.stmts.add(matchStmt);
        result = rewrite(safeAssignmentBlock, this.env);
    }

    @Override
    public void visit(BLangTupleDestructure stmt) {
        // var (a, b) = (tuple)
        //
        //  desugar once
        //  any[] x = (tuple);
        //  a = x[0];
        final BLangBlockStmt blockStmt = ASTBuilderUtil.createBlockStmt(stmt.pos);

        BType runTimeType = new BArrayType(symTable.anyType);
        final BLangVariable tuple = ASTBuilderUtil.createVariable(stmt.pos, "", runTimeType, null,
                new BVarSymbol(0, names.fromString("tuple"),
                        this.env.scope.owner.pkgID, runTimeType, this.env.scope.owner));
        tuple.expr = stmt.expr;
        final BLangVariableDef variableDef = ASTBuilderUtil.createVariableDefStmt(stmt.pos, blockStmt);
        variableDef.var = tuple;

        for (int index = 0; index < stmt.varRefs.size(); index++) {
            BLangExpression varRef = stmt.varRefs.get(index);
            BLangLiteral indexExpr = ASTBuilderUtil.createLiteral(stmt.pos, symTable.intType, (long) index);
            BLangIndexBasedAccess arrayAccess = ASTBuilderUtil.createIndexBasesAccessExpr(stmt.pos, symTable.anyType,
                    tuple.symbol, indexExpr);

            final BLangExpression assignmentExpr;
            if (types.isValueType(varRef.type)) {
                BLangTypeConversionExpr castExpr = (BLangTypeConversionExpr) TreeBuilder.createTypeConversionNode();
                castExpr.expr = arrayAccess;
                castExpr.conversionSymbol = Symbols.createUnboxValueTypeOpSymbol(symTable.anyType, varRef.type);
                castExpr.type = varRef.type;
                assignmentExpr = castExpr;
            } else {
                assignmentExpr = arrayAccess;
            }
            final BLangAssignment assignmentStmt = ASTBuilderUtil.createAssignmentStmt(stmt.pos, blockStmt);
            assignmentStmt.declaredWithVar = stmt.declaredWithVar;
            assignmentStmt.varRefs = Lists.of(varRef);
            assignmentStmt.expr = assignmentExpr;
        }
        result = rewrite(blockStmt, env);
    }

    @Override
    public void visit(BLangBind bindNode) {
        bindNode.varRef = rewriteExpr(bindNode.varRef);
        bindNode.expr = rewriteExpr(bindNode.expr);
        result = new BLangAssignment(bindNode.pos, Arrays.asList(bindNode.varRef), bindNode.expr, false);
    }

    @Override
    public void visit(BLangAbort abortNode) {
        result = abortNode;
    }

    @Override
    public void visit(BLangFail failNode) {
        result = failNode;
    }

    @Override
    public void visit(BLangNext nextNode) {
        result = nextNode;
    }

    @Override
    public void visit(BLangBreak breakNode) {
        result = breakNode;
    }

    @Override
    public void visit(BLangReturn returnNode) {
        if (returnNode.namedReturnVariables != null) {
            // Handled named returns.
            for (BLangVariable variable : returnNode.namedReturnVariables) {
                BLangSimpleVarRef varRef = (BLangSimpleVarRef) TreeBuilder.createSimpleVariableReferenceNode();
                varRef.variableName = variable.name;
                varRef.symbol = variable.symbol;
                varRef.type = variable.type;
                varRef.pos = returnNode.pos;
                returnNode.exprs.add(varRef);
            }
        }
        returnNode.exprs = rewriteExprs(returnNode.exprs);
        result = returnNode;
    }

    @Override
    public void visit(BLangThrow throwNode) {
        throwNode.expr = rewriteExpr(throwNode.expr);
        result = throwNode;
    }

    @Override
    public void visit(BLangXMLNSStatement xmlnsStmtNode) {
        xmlnsStmtNode.xmlnsDecl = rewrite(xmlnsStmtNode.xmlnsDecl, env);
        result = xmlnsStmtNode;
    }

    @Override
    public void visit(BLangXMLNS xmlnsNode) {
        BLangXMLNS generatedXMLNSNode;
        xmlnsNode.namespaceURI = rewriteExpr(xmlnsNode.namespaceURI);
        BSymbol ownerSymbol = xmlnsNode.symbol.owner;

        // Local namespace declaration in a function/resource/action/worker
        if ((ownerSymbol.tag & SymTag.INVOKABLE) == SymTag.INVOKABLE) {
            generatedXMLNSNode = new BLangLocalXMLNS();
        } else {
            generatedXMLNSNode = new BLangPackageXMLNS();
        }

        generatedXMLNSNode.namespaceURI = xmlnsNode.namespaceURI;
        generatedXMLNSNode.prefix = xmlnsNode.prefix;
        generatedXMLNSNode.symbol = xmlnsNode.symbol;
        result = generatedXMLNSNode;
    }

    public void visit(BLangCompoundAssignment compoundAssignment) {
        BLangAssignment assignStmt = (BLangAssignment) TreeBuilder.createAssignmentNode();
        assignStmt.pos = compoundAssignment.pos;
        assignStmt.addVariable(rewriteExpr((BLangVariableReference) compoundAssignment.varRef));
        assignStmt.expr = rewriteExpr(compoundAssignment.modifiedExpr);
        result = assignStmt;
    }

    public void visit(BLangPostIncrement postIncrement) {
        BLangAssignment assignStmt = (BLangAssignment) TreeBuilder.createAssignmentNode();
        assignStmt.pos = postIncrement.pos;
        assignStmt.addVariable(rewriteExpr((BLangVariableReference) postIncrement.varRef));
        assignStmt.expr = rewriteExpr(postIncrement.modifiedExpr);
        result = assignStmt;
    }

    @Override
    public void visit(BLangExpressionStmt exprStmtNode) {
        exprStmtNode.expr = rewriteExpr(exprStmtNode.expr);
        result = exprStmtNode;
    }

    @Override
    public void visit(BLangIf ifNode) {
        ifNode.expr = rewriteExpr(ifNode.expr);
        ifNode.body = rewrite(ifNode.body, env);
        ifNode.elseStmt = rewrite(ifNode.elseStmt, env);
        result = ifNode;
    }

    @Override
    public void visit(BLangMatch matchStmt) {
        // Here we generate an if-else statement for the match statement
        // Here is an example match statement
        //
        //      match expr {
        //          int k => io:println("int value: " + k);
        //          string s => io:println("string value: " + s);
        //          json j => io:println("json value: " + s);
        //
        //      }
        //
        //  Here is how we convert the match statement to an if-else statement. The last clause should always be the
        //  else clause
        //
        //  string | int | json | any _$$_matchexpr = expr;
        //  if ( _$$_matchexpr isassignable int ){
        //      int k = (int) _$$_matchexpr; // unbox
        //      io:println("int value: " + k);
        //
        //  } else if (_$$_matchexpr isassignable string ) {
        //      string s = (string) _$$_matchexpr; // unbox
        //      io:println("string value: " + s);
        //
        //  } else if ( _$$_matchexpr isassignable float ||    // should we consider json[] as well
        //                  _$$_matchexpr isassignable boolean ||
        //                  _$$_matchexpr isassignable json) {
        //
        //  } else {
        //      // handle the last pattern
        //      any case..
        //  }
        //

        // First create a block statement to hold generated statements
        BLangBlockStmt matchBlockStmt = (BLangBlockStmt) TreeBuilder.createBlockNode();
        matchBlockStmt.pos = matchStmt.pos;

        // Create a variable definition to store the value of the match expression
        String matchExprVarName = GEN_VAR_PREFIX.value;
        BLangVariable matchExprVar = ASTBuilderUtil.createVariable(matchStmt.expr.pos,
                matchExprVarName, matchStmt.expr.type, matchStmt.expr, new BVarSymbol(0,
                        names.fromString(matchExprVarName),
                        this.env.scope.owner.pkgID, matchStmt.expr.type, this.env.scope.owner));

        // Now create a variable definition node
        BLangVariableDef matchExprVarDef = ASTBuilderUtil.createVariableDef(matchBlockStmt.pos, matchExprVar);

        // Add the var def statement to the block statement
        //      string | int _$$_matchexpr = expr;
        matchBlockStmt.stmts.add(matchExprVarDef);

        // Create if/else blocks with typeof binary expressions for each pattern
        matchBlockStmt.stmts.add(generateIfElseStmt(matchStmt, matchExprVar));

        rewrite(matchBlockStmt, this.env);
        result = matchBlockStmt;
    }

    @Override
    public void visit(BLangForeach foreach) {
        foreach.varRefs = rewrite(foreach.varRefs, env);
        foreach.collection = rewriteExpr(foreach.collection);
        foreach.body = rewrite(foreach.body, env);
        result = foreach;
    }

    @Override
    public void visit(BLangWhile whileNode) {
        whileNode.expr = rewriteExpr(whileNode.expr);
        whileNode.body = rewrite(whileNode.body, env);
        result = whileNode;
    }

    @Override
    public void visit(BLangLock lockNode) {
        enclLocks.push(lockNode);
        lockNode.body = rewrite(lockNode.body, env);
        enclLocks.pop();
        lockNode.lockVariables = lockNode.lockVariables.stream().sorted((v1, v2) -> {
            String o1FullName = String.join(":", v1.pkgID.getName().getValue(), v1.name.getValue());
            String o2FullName = String.join(":", v2.pkgID.getName().getValue(), v2.name.getValue());
            return o1FullName.compareTo(o2FullName);
        }).collect(Collectors.toSet());
        result = lockNode;
    }

    @Override
    public void visit(BLangTransaction transactionNode) {
        transactionNode.transactionBody = rewrite(transactionNode.transactionBody, env);
        transactionNode.onRetryBody = rewrite(transactionNode.onRetryBody, env);
        transactionNode.retryCount = rewriteExpr(transactionNode.retryCount);
        transactionNode.onCommitFunction = rewriteExpr(transactionNode.onCommitFunction);
        transactionNode.onAbortFunction = rewriteExpr(transactionNode.onAbortFunction);
        result = transactionNode;
    }

    @Override
    public void visit(BLangTryCatchFinally tryNode) {
        tryNode.tryBody = rewrite(tryNode.tryBody, env);
        tryNode.catchBlocks = rewrite(tryNode.catchBlocks, env);
        tryNode.finallyBody = rewrite(tryNode.finallyBody, env);
        result = tryNode;
    }

    @Override
    public void visit(BLangCatch catchNode) {
        catchNode.body = rewrite(catchNode.body, env);
        result = catchNode;
    }

    @Override
    public void visit(BLangForkJoin forkJoin) {
        forkJoin.workers = rewrite(forkJoin.workers, env);
        forkJoin.joinResultVar = rewrite(forkJoin.joinResultVar, env);
        forkJoin.joinedBody = rewrite(forkJoin.joinedBody, env);
        forkJoin.timeoutBody = rewrite(forkJoin.timeoutBody, env);
        result = forkJoin;
    }

    // Expressions

    @Override
    public void visit(BLangLiteral literalExpr) {
        result = literalExpr;
    }

    @Override
    public void visit(BLangArrayLiteral arrayLiteral) {
        arrayLiteral.exprs = rewriteExprs(arrayLiteral.exprs);

        if (arrayLiteral.type.tag == TypeTags.JSON || getElementType(arrayLiteral.type).tag == TypeTags.JSON) {
            result = new BLangJSONArrayLiteral(arrayLiteral.exprs, arrayLiteral.type);
            return;
        }
        result = arrayLiteral;
    }

    @Override
    public void visit(BLangRecordLiteral recordLiteral) {
        recordLiteral.keyValuePairs.forEach(keyValue -> {
            BLangExpression keyExpr = keyValue.key.expr;
            if (keyExpr.getKind() == NodeKind.SIMPLE_VARIABLE_REF) {
                BLangSimpleVarRef varRef = (BLangSimpleVarRef) keyExpr;
                keyValue.key.expr = createStringLiteral(varRef.pos, varRef.variableName.value);
            } else {
                keyValue.key.expr = rewriteExpr(keyValue.key.expr);
            }

            keyValue.valueExpr = rewriteExpr(keyValue.valueExpr);
        });

        BLangExpression expr;
        if (recordLiteral.type.tag == TypeTags.STRUCT) {
            expr = new BLangStructLiteral(recordLiteral.keyValuePairs, recordLiteral.type);
        } else if (recordLiteral.type.tag == TypeTags.MAP) {
            expr = new BLangMapLiteral(recordLiteral.keyValuePairs, recordLiteral.type);
        } else if (recordLiteral.type.tag == TypeTags.TABLE) {
            expr = new BLangTableLiteral(recordLiteral.type);
        } else if (recordLiteral.type.tag == TypeTags.STREAM) {
            expr = new BLangStreamLiteral(recordLiteral.type, recordLiteral.name);
        } else {
            expr = new BLangJSONLiteral(recordLiteral.keyValuePairs, recordLiteral.type);
        }

        result = rewriteExpr(expr);
    }

    @Override
    public void visit(BLangTableLiteral tableLiteral) {
        result = tableLiteral;
    }

    private BLangInvocation createInvocationForWheneverBlock(BLangWhenever whenever) {
        List<BLangExpression> args = new ArrayList<>();
        List<BType> retTypes = new ArrayList<>();
        retTypes.add(symTable.noType);
        BLangLiteral streamingQueryLiteral = ASTBuilderUtil.createLiteral(whenever.pos, symTable.stringType, whenever
                .getSiddhiQuery());
        args.add(streamingQueryLiteral);
        addReferenceVariablesToArgs(args, siddhiQueryBuilder.getInStreamRefs());
        addReferenceVariablesToArgs(args, siddhiQueryBuilder.getInTableRefs());
        addReferenceVariablesToArgs(args, siddhiQueryBuilder.getOutStreamRefs());
        addReferenceVariablesToArgs(args, siddhiQueryBuilder.getOutTableRefs());
        addFunctionPointersToArgs(args, whenever.gettreamingQueryStatements());
        return createInvocationNode(CREATE_WHENEVER, args, retTypes);
    }

    private void addReferenceVariablesToArgs(List<BLangExpression> args, List<BLangExpression> varRefs) {
        BLangArrayLiteral localRefs = createArrayLiteralExprNode();
        varRefs.forEach(varRef -> localRefs.exprs.add(rewrite(varRef, env)));
        args.add(localRefs);
    }

    private void addFunctionPointersToArgs(List<BLangExpression> args, List<StreamingQueryStatementNode>
            streamingStmts) {
        BLangArrayLiteral funcPointers = createArrayLiteralExprNode();
        for (StreamingQueryStatementNode stmt : streamingStmts) {
            funcPointers.exprs.add(rewrite((BLangExpression) stmt.getStreamingAction().getInvokableBody(), env));
        }
        args.add(funcPointers);
    }

    @Override
    public void visit(BLangSimpleVarRef varRefExpr) {
        BLangSimpleVarRef genVarRefExpr = varRefExpr;

        // XML qualified name reference. e.g: ns0:foo
        if (varRefExpr.pkgSymbol != null && varRefExpr.pkgSymbol.tag == SymTag.XMLNS) {
            BLangXMLQName qnameExpr = new BLangXMLQName(varRefExpr.variableName);
            qnameExpr.nsSymbol = (BXMLNSSymbol) varRefExpr.pkgSymbol;
            qnameExpr.localname = varRefExpr.variableName;
            qnameExpr.prefix = varRefExpr.pkgAlias;
            qnameExpr.namespaceURI = qnameExpr.nsSymbol.namespaceURI;
            qnameExpr.isUsedInXML = false;
            qnameExpr.pos = varRefExpr.pos;
            qnameExpr.type = symTable.stringType;
            result = qnameExpr;
            return;
        }

        BSymbol ownerSymbol = varRefExpr.symbol.owner;
        if ((varRefExpr.symbol.tag & SymTag.FUNCTION) == SymTag.FUNCTION &&
                varRefExpr.symbol.type.tag == TypeTags.INVOKABLE) {
            genVarRefExpr = new BLangFunctionVarRef(varRefExpr.symbol);
        } else if ((ownerSymbol.tag & SymTag.INVOKABLE) == SymTag.INVOKABLE) {
            // Local variable in a function/resource/action/worker
            genVarRefExpr = new BLangLocalVarRef(varRefExpr.symbol);
        } else if ((ownerSymbol.tag & SymTag.CONNECTOR) == SymTag.CONNECTOR) {
            // Field variable in a receiver
            genVarRefExpr = new BLangFieldVarRef(varRefExpr.symbol);
        } else if ((ownerSymbol.tag & SymTag.OBJECT) == SymTag.OBJECT) {
            genVarRefExpr = new BLangFieldVarRef(varRefExpr.symbol);
        } else if ((ownerSymbol.tag & SymTag.PACKAGE) == SymTag.PACKAGE ||
                (ownerSymbol.tag & SymTag.SERVICE) == SymTag.SERVICE) {
            // Package variable | service variable
            // We consider both of them as package level variables
            genVarRefExpr = new BLangPackageVarRef(varRefExpr.symbol);

            //Only locking service level and package level variables
            if (!enclLocks.isEmpty()) {
                enclLocks.peek().addLockVariable(varRefExpr.symbol);
            }
        }

        genVarRefExpr.type = varRefExpr.type;
        result = genVarRefExpr;
    }

    @Override
    public void visit(BLangFieldBasedAccess fieldAccessExpr) {
        BLangVariableReference targetVarRef = fieldAccessExpr;
        if (fieldAccessExpr.expr.type.tag == TypeTags.ENUM) {
            targetVarRef = new BLangEnumeratorAccessExpr(fieldAccessExpr.pos,
                    fieldAccessExpr.field, fieldAccessExpr.symbol);
        } else {
            fieldAccessExpr.expr = rewriteExpr(fieldAccessExpr.expr);
            BType varRefType = fieldAccessExpr.expr.type;
            if (varRefType.tag == TypeTags.STRUCT) {
                targetVarRef = new BLangStructFieldAccessExpr(fieldAccessExpr.pos,
                        fieldAccessExpr.expr, fieldAccessExpr.symbol);
            } else if (varRefType.tag == TypeTags.MAP) {
                BLangLiteral stringLit = createStringLiteral(fieldAccessExpr.pos, fieldAccessExpr.field.value);
                targetVarRef = new BLangMapAccessExpr(fieldAccessExpr.pos, fieldAccessExpr.expr, stringLit);
            } else if (varRefType.tag == TypeTags.JSON) {
                BLangLiteral stringLit = createStringLiteral(fieldAccessExpr.pos, fieldAccessExpr.field.value);
                targetVarRef = new BLangJSONAccessExpr(fieldAccessExpr.pos, fieldAccessExpr.expr, stringLit);
            }
        }

        targetVarRef.lhsVar = fieldAccessExpr.lhsVar;
        targetVarRef.type = fieldAccessExpr.type;
        result = targetVarRef;
    }

    @Override
    public void visit(BLangIndexBasedAccess indexAccessExpr) {
        BLangVariableReference targetVarRef = indexAccessExpr;
        indexAccessExpr.indexExpr = rewriteExpr(indexAccessExpr.indexExpr);
        indexAccessExpr.expr = rewriteExpr(indexAccessExpr.expr);
        BType varRefType = indexAccessExpr.expr.type;
        if (varRefType.tag == TypeTags.STRUCT) {
            targetVarRef = new BLangStructFieldAccessExpr(indexAccessExpr.pos,
                    indexAccessExpr.expr, indexAccessExpr.symbol);
        } else if (varRefType.tag == TypeTags.MAP) {
            targetVarRef = new BLangMapAccessExpr(indexAccessExpr.pos,
                    indexAccessExpr.expr, indexAccessExpr.indexExpr);
        } else if (varRefType.tag == TypeTags.JSON || getElementType(varRefType).tag == TypeTags.JSON) {
            targetVarRef = new BLangJSONAccessExpr(indexAccessExpr.pos, indexAccessExpr.expr,
                    indexAccessExpr.indexExpr);
        } else if (varRefType.tag == TypeTags.ARRAY) {
            targetVarRef = new BLangArrayAccessExpr(indexAccessExpr.pos,
                    indexAccessExpr.expr, indexAccessExpr.indexExpr);
        } else if (varRefType.tag == TypeTags.XML) {
            targetVarRef = new BLangXMLAccessExpr(indexAccessExpr.pos,
                    indexAccessExpr.expr, indexAccessExpr.indexExpr);
        }

        targetVarRef.lhsVar = indexAccessExpr.lhsVar;
        targetVarRef.type = indexAccessExpr.type;
        result = targetVarRef;
    }

    @Override
    public void visit(BLangInvocation iExpr) {
        BLangInvocation genIExpr = iExpr;

        // Reorder the arguments to match the original function signature.
        reorderArguments(iExpr);
        iExpr.requiredArgs = rewriteExprs(iExpr.requiredArgs);
        iExpr.namedArgs = rewriteExprs(iExpr.namedArgs);
        iExpr.restArgs = rewriteExprs(iExpr.restArgs);

        if (iExpr.functionPointerInvocation) {
            visitFunctionPointerInvocation(iExpr);
            return;
        } else if (iExpr.iterableOperationInvocation) {
            visitIterableOperationInvocation(iExpr);
            return;
        }
        if (iExpr.actionInvocation) {
            visitActionInvocationEndpoint(iExpr);
        }
        iExpr.expr = rewriteExpr(iExpr.expr);
        result = genIExpr;
        if (iExpr.expr == null) {
            return;
        }

        switch (iExpr.expr.type.tag) {
            case TypeTags.BOOLEAN:
            case TypeTags.STRING:
            case TypeTags.INT:
            case TypeTags.FLOAT:
            case TypeTags.BLOB:
            case TypeTags.JSON:
            case TypeTags.XML:
            case TypeTags.MAP:
            case TypeTags.TABLE:
            case TypeTags.STREAM:
            case TypeTags.FUTURE:
            case TypeTags.STRUCT:
                List<BLangExpression> argExprs = new ArrayList<>(iExpr.requiredArgs);
                argExprs.add(0, iExpr.expr);
                result = new BLangAttachedFunctionInvocation(iExpr.pos, argExprs, iExpr.namedArgs, iExpr.restArgs,
                        iExpr.symbol, iExpr.types, iExpr.expr);
                break;
            case TypeTags.CONNECTOR:
                List<BLangExpression> actionArgExprs = new ArrayList<>(iExpr.requiredArgs);
                actionArgExprs.add(0, iExpr.expr);
                result = new BLangActionInvocation(iExpr.pos, actionArgExprs, iExpr.namedArgs, iExpr.restArgs,
                        iExpr.symbol, iExpr.types);
                break;
        }
    }

    public void visit(BLangTypeInit connectorInitExpr) {
        connectorInitExpr.argsExpr = rewriteExprs(connectorInitExpr.argsExpr);
        connectorInitExpr.objectInitInvocation = rewriteExpr(connectorInitExpr.objectInitInvocation);
        result = connectorInitExpr;
    }

    @Override
    public void visit(BLangTernaryExpr ternaryExpr) {
        ternaryExpr.expr = rewriteExpr(ternaryExpr.expr);
        ternaryExpr.thenExpr = rewriteExpr(ternaryExpr.thenExpr);
        ternaryExpr.elseExpr = rewriteExpr(ternaryExpr.elseExpr);
        result = ternaryExpr;
    }

    @Override
    public void visit(BLangAwaitExpr awaitExpr) {
        awaitExpr.expr = rewriteExpr(awaitExpr.expr);
        result = awaitExpr;
    }

    @Override
    public void visit(BLangBinaryExpr binaryExpr) {
        binaryExpr.lhsExpr = rewriteExpr(binaryExpr.lhsExpr);
        binaryExpr.rhsExpr = rewriteExpr(binaryExpr.rhsExpr);
        result = binaryExpr;

        // Check lhs and rhs type compatibility
        if (binaryExpr.lhsExpr.type.tag == binaryExpr.rhsExpr.type.tag) {
            return;
        }

        if (binaryExpr.lhsExpr.type.tag == TypeTags.STRING && binaryExpr.opKind == OperatorKind.ADD) {
            binaryExpr.rhsExpr = createTypeConversionExpr(binaryExpr.rhsExpr,
                    binaryExpr.rhsExpr.type, binaryExpr.lhsExpr.type);
            return;
        }

        if (binaryExpr.rhsExpr.type.tag == TypeTags.STRING && binaryExpr.opKind == OperatorKind.ADD) {
            binaryExpr.lhsExpr = createTypeConversionExpr(binaryExpr.lhsExpr,
                    binaryExpr.lhsExpr.type, binaryExpr.rhsExpr.type);
            return;
        }

        if (binaryExpr.lhsExpr.type.tag == TypeTags.FLOAT) {
            binaryExpr.rhsExpr = createTypeConversionExpr(binaryExpr.rhsExpr,
                    binaryExpr.rhsExpr.type, binaryExpr.lhsExpr.type);
            return;
        }

        if (binaryExpr.rhsExpr.type.tag == TypeTags.FLOAT) {
            binaryExpr.lhsExpr = createTypeConversionExpr(binaryExpr.lhsExpr,
                    binaryExpr.lhsExpr.type, binaryExpr.rhsExpr.type);
        }
    }

    @Override
    public void visit(BLangBracedOrTupleExpr bracedOrTupleExpr) {
        if (bracedOrTupleExpr.isBracedExpr) {
            result = rewriteExpr(bracedOrTupleExpr.expressions.get(0));
            return;
        }
        bracedOrTupleExpr.expressions.forEach(expr -> types.setImplicitCastExpr(expr, expr.type, symTable.anyType));
        bracedOrTupleExpr.expressions = rewriteExprs(bracedOrTupleExpr.expressions);
        result = bracedOrTupleExpr;
    }

    @Override
    public void visit(BLangUnaryExpr unaryExpr) {
        unaryExpr.expr = rewriteExpr(unaryExpr.expr);
        if (unaryExpr.expr.getKind() == NodeKind.TYPEOF_EXPRESSION) {
            result = unaryExpr.expr;
        } else {
            result = unaryExpr;
        }
    }

    @Override
    public void visit(BLangTypeCastExpr castExpr) {
        castExpr.expr = rewriteExpr(castExpr.expr);
        result = castExpr;
    }

    @Override
    public void visit(BLangTypeConversionExpr conversionExpr) {
        conversionExpr.expr = rewriteExpr(conversionExpr.expr);

        // Built-in conversion
        if (conversionExpr.conversionSymbol.tag != SymTag.TRANSFORMER) {
            result = conversionExpr;
            return;
        }

        // Named transformer invocation
        BLangInvocation transformerInvoc = conversionExpr.transformerInvocation;
        if (transformerInvoc != null) {
            transformerInvoc = rewriteExpr(transformerInvoc);
            // Add the rExpr as the first argument
            conversionExpr.transformerInvocation.requiredArgs.add(0, conversionExpr.expr);
            result = new BLangTransformerInvocation(conversionExpr.pos, transformerInvoc.requiredArgs,
                    transformerInvoc.namedArgs, transformerInvoc.restArgs, transformerInvoc.symbol,
                    conversionExpr.types);
            conversionExpr.transformerInvocation = transformerInvoc;
            return;
        }

        // Unnamed transformer invocation
        BConversionOperatorSymbol transformerSym = conversionExpr.conversionSymbol;
        transformerInvoc = new BLangTransformerInvocation(conversionExpr.pos,
                Lists.of(conversionExpr.expr), transformerSym, conversionExpr.types);
        transformerInvoc.types = transformerSym.type.getReturnTypes();
        result = transformerInvoc;
    }

    @Override
    public void visit(BLangLambdaFunction bLangLambdaFunction) {
        result = bLangLambdaFunction;
    }

    @Override
    public void visit(BLangXMLQName xmlQName) {
        result = xmlQName;
    }

    @Override
    public void visit(BLangXMLAttribute xmlAttribute) {
        xmlAttribute.name = rewriteExpr(xmlAttribute.name);
        xmlAttribute.value = rewriteExpr(xmlAttribute.value);
        result = xmlAttribute;
    }

    @Override
    public void visit(BLangXMLElementLiteral xmlElementLiteral) {
        xmlElementLiteral.startTagName = rewriteExpr(xmlElementLiteral.startTagName);
        xmlElementLiteral.endTagName = rewriteExpr(xmlElementLiteral.endTagName);
        xmlElementLiteral.modifiedChildren = rewriteExprs(xmlElementLiteral.modifiedChildren);
        xmlElementLiteral.attributes = rewriteExprs(xmlElementLiteral.attributes);

        // Separate the in-line namepsace declarations and attributes.
        Iterator<BLangXMLAttribute> attributesItr = xmlElementLiteral.attributes.iterator();
        while (attributesItr.hasNext()) {
            BLangXMLAttribute attribute = attributesItr.next();
            if (!attribute.isNamespaceDeclr) {
                continue;
            }

            // Create local namepace declaration for all in-line namespace declarations
            BLangLocalXMLNS xmlns = new BLangLocalXMLNS();
            xmlns.namespaceURI = attribute.value.concatExpr;
            xmlns.prefix = ((BLangXMLQName) attribute.name).localname;
            xmlns.symbol = (BXMLNSSymbol) attribute.symbol;

            xmlElementLiteral.inlineNamespaces.add(xmlns);
            attributesItr.remove();
        }

        result = xmlElementLiteral;
    }

    @Override
    public void visit(BLangXMLTextLiteral xmlTextLiteral) {
        xmlTextLiteral.concatExpr = rewriteExpr(xmlTextLiteral.concatExpr);
        result = xmlTextLiteral;
    }

    @Override
    public void visit(BLangXMLCommentLiteral xmlCommentLiteral) {
        xmlCommentLiteral.concatExpr = rewriteExpr(xmlCommentLiteral.concatExpr);
        result = xmlCommentLiteral;
    }

    @Override
    public void visit(BLangXMLProcInsLiteral xmlProcInsLiteral) {
        xmlProcInsLiteral.target = rewriteExpr(xmlProcInsLiteral.target);
        xmlProcInsLiteral.dataConcatExpr = rewriteExpr(xmlProcInsLiteral.dataConcatExpr);
        result = xmlProcInsLiteral;
    }

    @Override
    public void visit(BLangXMLQuotedString xmlQuotedString) {
        xmlQuotedString.concatExpr = rewriteExpr(xmlQuotedString.concatExpr);
        result = xmlQuotedString;
    }

    @Override
    public void visit(BLangStringTemplateLiteral stringTemplateLiteral) {
        stringTemplateLiteral.concatExpr = rewriteExpr(stringTemplateLiteral.concatExpr);
        result = stringTemplateLiteral;
    }

    @Override
    public void visit(BLangWorkerSend workerSendNode) {
        workerSendNode.exprs = rewriteExprs(workerSendNode.exprs);
        result = workerSendNode;
    }

    @Override
    public void visit(BLangWorkerReceive workerReceiveNode) {
        workerReceiveNode.exprs = rewriteExprs(workerReceiveNode.exprs);
        result = workerReceiveNode;
    }

    @Override
    public void visit(BLangXMLAttributeAccess xmlAttributeAccessExpr) {
        xmlAttributeAccessExpr.indexExpr = rewriteExpr(xmlAttributeAccessExpr.indexExpr);
        xmlAttributeAccessExpr.expr = rewriteExpr(xmlAttributeAccessExpr.expr);

        if (xmlAttributeAccessExpr.indexExpr != null
                && xmlAttributeAccessExpr.indexExpr.getKind() == NodeKind.XML_QNAME) {
            ((BLangXMLQName) xmlAttributeAccessExpr.indexExpr).isUsedInXML = true;
        }

        result = xmlAttributeAccessExpr;
    }

    // Generated expressions. Following expressions are not part of the original syntax
    // tree which is coming out of the parser

    @Override
    public void visit(BLangLocalVarRef localVarRef) {
        result = localVarRef;
    }

    @Override
    public void visit(BLangFieldVarRef fieldVarRef) {
        result = fieldVarRef;
    }

    @Override
    public void visit(BLangPackageVarRef packageVarRef) {
        result = packageVarRef;
    }

    @Override
    public void visit(BLangFunctionVarRef functionVarRef) {
        result = functionVarRef;
    }

    @Override
    public void visit(BLangStructFieldAccessExpr fieldAccessExpr) {
        result = fieldAccessExpr;
    }

    @Override
    public void visit(BLangMapAccessExpr mapKeyAccessExpr) {
        result = mapKeyAccessExpr;
    }

    @Override
    public void visit(BLangArrayAccessExpr arrayIndexAccessExpr) {
        result = arrayIndexAccessExpr;
    }

    @Override
    public void visit(BLangJSONLiteral jsonLiteral) {
        result = jsonLiteral;
    }

    @Override
    public void visit(BLangMapLiteral mapLiteral) {
        result = mapLiteral;
    }

    public void visit(BLangStreamLiteral streamLiteral) {
        result = streamLiteral;
    }

    @Override
    public void visit(BLangStructLiteral structLiteral) {
        List<String> keys = new ArrayList<>();
        structLiteral.keyValuePairs.forEach(keyVal -> {
            keys.add(keyVal.key.fieldSymbol.name.value);
        });

        result = structLiteral;
    }

    @Override
    public void visit(BLangIsAssignableExpr assignableExpr) {
        assignableExpr.lhsExpr = rewriteExpr(assignableExpr.lhsExpr);
        result = assignableExpr;
    }

    @Override
    public void visit(BFunctionPointerInvocation fpInvocation) {
        result = fpInvocation;
    }

    @Override
    public void visit(BLangTypeofExpr accessExpr) {
        result = accessExpr;
    }

    @Override
    public void visit(BLangIntRangeExpression intRangeExpression) {
        intRangeExpression.startExpr = rewriteExpr(intRangeExpression.startExpr);
        intRangeExpression.endExpr = rewriteExpr(intRangeExpression.endExpr);
        result = intRangeExpression;
    }

    @Override
    public void visit(BLangRestArgsExpression bLangVarArgsExpression) {
        result = rewriteExpr(bLangVarArgsExpression.expr);
    }

    @Override
    public void visit(BLangNamedArgsExpression bLangNamedArgsExpression) {
        bLangNamedArgsExpression.expr = rewriteExpr(bLangNamedArgsExpression.expr);
        result = bLangNamedArgsExpression.expr;
    }

    public void visit(BLangTableQueryExpression tableQueryExpression) {
        sqlQueryBuilder.visit(tableQueryExpression);

        /*replace the table expression with a function invocation,
         so that we manually call a native function "queryTable". */
        result = createInvocationFromTableExpr(tableQueryExpression);
    }

    private BLangInvocation createInvocationFromTableExpr(BLangTableQueryExpression tableQueryExpression) {
        List<BLangExpression> args = new ArrayList<>();
        List<BType> retTypes = new ArrayList<>();
        String functionName = QUERY_TABLE_WITHOUT_JOIN_CLAUSE;
        //Order matters, because these are the args for a function invocation.
        args.add(getSQLPreparedStatement(tableQueryExpression));
        args.add(getFromTableVarRef(tableQueryExpression));
       // BLangTypeofExpr
        retTypes.add(tableQueryExpression.type);
        BLangSimpleVarRef joinTable = getJoinTableVarRef(tableQueryExpression);
        if (joinTable != null) {
            args.add(joinTable);
            functionName = QUERY_TABLE_WITH_JOIN_CLAUSE;
        }
        args.add(getSQLStatementParameters(tableQueryExpression));
        args.add(getReturnType(tableQueryExpression));
        return createInvocationNode(functionName, args, retTypes);
    }

    private BLangInvocation createInvocationNode(String functionName, List<BLangExpression> args, List<BType>
            retTypes) {
        BLangInvocation invocationNode = (BLangInvocation) TreeBuilder.createInvocationNode();
        BLangIdentifier name = (BLangIdentifier) TreeBuilder.createIdentifierNode();
        name.setLiteral(false);
        name.setValue(functionName);
        invocationNode.name = name;
        invocationNode.pkgAlias = (BLangIdentifier) TreeBuilder.createIdentifierNode();

        // TODO: 2/28/18 need to find a good way to refer to symbols
        invocationNode.symbol = symTable.rootScope.lookup(new Name(functionName)).symbol;
        invocationNode.types = retTypes;
        invocationNode.requiredArgs = args;
        return invocationNode;
    }

    private BLangLiteral getSQLPreparedStatement(BLangTableQueryExpression
                                                         tableQueryExpression) {
        //create a literal to represent the sql query.
        BLangLiteral sqlQueryLiteral = (BLangLiteral) TreeBuilder.createLiteralExpression();
        sqlQueryLiteral.typeTag = TypeTags.STRING;

        //assign the sql query from table expression to the literal.
        sqlQueryLiteral.value = tableQueryExpression.getSqlQuery();
        sqlQueryLiteral.type = symTable.getTypeFromTag(sqlQueryLiteral.typeTag);
        return sqlQueryLiteral;
    }

    private BLangStructLiteral getReturnType(BLangTableQueryExpression
                                                     tableQueryExpression) {
        //create a literal to represent the sql query.
        BTableType tableType = (BTableType) tableQueryExpression.type;
        BStructType structType = (BStructType) tableType.constraint;
        return new BLangStructLiteral(new ArrayList<>(), structType);
    }

    private BLangArrayLiteral getSQLStatementParameters(BLangTableQueryExpression tableQueryExpression) {
        BLangArrayLiteral expr = createArrayLiteralExprNode();
        List<BLangExpression> params = tableQueryExpression.getParams();

        params.stream().map(param -> (BLangLiteral) param).forEach(literal -> {
            Object value = literal.getValue();
            int type = TypeTags.STRING;
            if (value instanceof Integer || value instanceof Long) {
                type = TypeTags.INT;
            } else if (value instanceof Double || value instanceof Float) {
                type = TypeTags.FLOAT;
            } else if (value instanceof Boolean) {
                type = TypeTags.BOOLEAN;
            } else if (value instanceof Byte[]) {
                type = TypeTags.BLOB;
            } else if (value instanceof Object[]) {
                type = TypeTags.ARRAY;
            }
            literal.type = symTable.getTypeFromTag(type);
            types.setImplicitCastExpr(literal, new BType(type, null), symTable.anyType);
            expr.exprs.add(literal.impConversionExpr);
        });
        return expr;
    }

    private BLangArrayLiteral createArrayLiteralExprNode() {
        BLangArrayLiteral expr = (BLangArrayLiteral) TreeBuilder.createArrayLiteralNode();
        expr.exprs = new ArrayList<>();
        expr.type = symTable.anyType;
        return expr;
    }

    private BLangSimpleVarRef getJoinTableVarRef(BLangTableQueryExpression tableQueryExpression) {
        JoinStreamingInput joinStreamingInput = tableQueryExpression.getTableQuery().getJoinStreamingInput();
        BLangSimpleVarRef joinTable = null;
        if (joinStreamingInput != null) {
            joinTable = (BLangSimpleVarRef) joinStreamingInput.getStreamingInput().getStreamReference();
            joinTable = rewrite(joinTable, env);
        }
        return joinTable;
    }

    private BLangSimpleVarRef getFromTableVarRef(BLangTableQueryExpression tableQueryExpression) {
        BLangSimpleVarRef fromTable = (BLangSimpleVarRef) tableQueryExpression.getTableQuery().getStreamingInput()
                .getStreamReference();
        return rewrite(fromTable, env);
    }

    // private functions

    private void visitFunctionPointerInvocation(BLangInvocation iExpr) {
        BLangVariableReference expr;
        if (iExpr.expr == null) {
            expr = new BLangSimpleVarRef();
        } else {
            BLangFieldBasedAccess fieldBasedAccess = new BLangFieldBasedAccess();
            fieldBasedAccess.expr = iExpr.expr;
            fieldBasedAccess.field = iExpr.name;
            expr = fieldBasedAccess;
        }
        expr.symbol = (BVarSymbol) iExpr.symbol;
        expr.type = iExpr.symbol.type;
        expr = rewriteExpr(expr);
        result = new BFunctionPointerInvocation(iExpr, expr);
    }

    private void visitIterableOperationInvocation(BLangInvocation iExpr) {
        if (iExpr.iContext.operations.getLast().iExpr != iExpr) {
            result = null;
            return;
        }
        iterableCodeDesugar.desugar(iExpr.iContext);
        result = rewriteExpr(iExpr.iContext.iteratorCaller);
    }

    private void visitActionInvocationEndpoint(BLangInvocation iExpr) {
        final BEndpointVarSymbol epSymbol = (BEndpointVarSymbol) iExpr.expr.symbol;
        // Convert to endpoint.getClient(). iExpr has to be a VarRef.
        final BLangInvocation getClientExpr = ASTBuilderUtil.createInvocationExpr(iExpr.expr.pos,
                epSymbol.getClientFunction, Collections.emptyList(), symResolver);
        getClientExpr.expr = iExpr.expr;
        iExpr.expr = getClientExpr;
    }

    @SuppressWarnings("unchecked")
    private <E extends BLangNode> E rewrite(E node, SymbolEnv env) {
        if (node == null) {
            return null;
        }

        if (node.desugared) {
            return node;
        }

        SymbolEnv previousEnv = this.env;
        this.env = env;

        node.accept(this);
        BLangNode resultNode = this.result;
        this.result = null;
        resultNode.desugared = true;

        this.env = previousEnv;
        return (E) resultNode;
    }

    @SuppressWarnings("unchecked")
    private <E extends BLangExpression> E rewriteExpr(E node) {
        if (node == null) {
            return null;
        }

        if (node.desugared) {
            return node;
        }

        BLangExpression expr = node;
        if (node.impConversionExpr != null) {
            expr = node.impConversionExpr;
            node.impConversionExpr = null;
        }

        expr.accept(this);
        BLangNode resultNode = this.result;
        this.result = null;
        resultNode.desugared = true;
        return (E) resultNode;
    }

    @SuppressWarnings("unchecked")
    private <E extends BLangStatement> E rewrite(E statement, SymbolEnv env) {
        if (statement == null) {
            return null;
        }
        BLangStatementLink link = new BLangStatementLink();
        link.parent = currentLink;
        currentLink = link;
        BLangStatement stmt = (BLangStatement) rewrite((BLangNode) statement, env);
        // Link Statements.
        link.statement = stmt;
        stmt.statementLink = link;
        currentLink = link.parent;
        return (E) stmt;
    }

    private <E extends BLangStatement> List<E> rewriteStmt(List<E> nodeList, SymbolEnv env) {
        for (int i = 0; i < nodeList.size(); i++) {
            nodeList.set(i, rewrite(nodeList.get(i), env));
        }
        return nodeList;
    }

    private <E extends BLangNode> List<E> rewrite(List<E> nodeList, SymbolEnv env) {
        for (int i = 0; i < nodeList.size(); i++) {
            nodeList.set(i, rewrite(nodeList.get(i), env));
        }
        return nodeList;
    }

    private <E extends BLangExpression> List<E> rewriteExprs(List<E> nodeList) {
        for (int i = 0; i < nodeList.size(); i++) {
            nodeList.set(i, rewriteExpr(nodeList.get(i)));
        }
        return nodeList;
    }

    private BLangLiteral createStringLiteral(DiagnosticPos pos, String value) {
        BLangLiteral stringLit = new BLangLiteral();
        stringLit.pos = pos;
        stringLit.value = value;
        stringLit.type = symTable.stringType;
        return stringLit;
    }

    private BLangExpression createTypeConversionExpr(BLangExpression expr, BType sourceType, BType targetType) {
        BConversionOperatorSymbol symbol = (BConversionOperatorSymbol)
                symResolver.resolveConversionOperator(sourceType, targetType);
        BLangTypeConversionExpr conversionExpr = (BLangTypeConversionExpr) TreeBuilder.createTypeConversionNode();
        conversionExpr.pos = expr.pos;
        conversionExpr.expr = expr;
        conversionExpr.type = targetType;
        conversionExpr.conversionSymbol = symbol;
        return conversionExpr;
    }

    private BType getElementType(BType type) {
        if (type.tag != TypeTags.ARRAY) {
            return type;
        }

        return getElementType(((BArrayType) type).getElementType());
    }

    private void addReturnIfNotPresent(BLangInvokableNode invokableNode) {
        if (Symbols.isNative(invokableNode.symbol)) {
            return;
        }
        //This will only check whether last statement is a return and just add a return statement.
        //This won't analyse if else blocks etc to see whether return statements are present
        BLangBlockStmt blockStmt = invokableNode.body;
        if (invokableNode.workers.size() == 0 && invokableNode.retParams.isEmpty() && (blockStmt.stmts.size() < 1
                || blockStmt.stmts.get(blockStmt.stmts.size() - 1).getKind() != NodeKind.RETURN)) {
            BLangReturn returnStmt = (BLangReturn) TreeBuilder.createReturnNode();
            DiagnosticPos invPos = invokableNode.pos;
            DiagnosticPos pos = new DiagnosticPos(invPos.src, invPos.eLine, invPos.eLine, invPos.sCol, invPos.sCol);
            returnStmt.pos = pos;
            blockStmt.addStatement(returnStmt);
        }
    }

    private void addTransformerReturn(BLangTransformer transformerNode) {
        //This will only check whether last statement is a return and just add a return statement.
        //This won't analyze if else blocks etc to see whether return statements are present
        BLangBlockStmt blockStmt = transformerNode.body;
        if (transformerNode.workers.size() == 0 && (blockStmt.stmts.size() < 1
                || blockStmt.stmts.get(blockStmt.stmts.size() - 1).getKind() != NodeKind.RETURN)) {
            BLangReturn returnStmt = (BLangReturn) TreeBuilder.createReturnNode();
            DiagnosticPos invPos = transformerNode.pos;
            DiagnosticPos pos = new DiagnosticPos(invPos.src, invPos.eLine, invPos.eLine, invPos.sCol, invPos.sCol);
            returnStmt.pos = pos;

            if (!transformerNode.retParams.isEmpty()) {
                returnStmt.namedReturnVariables = transformerNode.retParams;
            }
            blockStmt.addStatement(returnStmt);
        }
    }

    private void addArgInitExpr(BLangTransformer transformerNode, BLangVariable var) {
        BLangSimpleVarRef varRef = new BLangLocalVarRef(var.symbol);
        varRef.lhsVar = true;
        varRef.pos = var.pos;
        varRef.type = var.type;

        BLangExpression initExpr = null;
        switch (var.type.tag) {
            case TypeTags.MAP:
                initExpr = new BLangMapLiteral(new ArrayList<>(), var.type);
                break;
            case TypeTags.JSON:
                initExpr = new BLangJSONLiteral(new ArrayList<>(), var.type);
                break;
            case TypeTags.STRUCT:
                initExpr = new BLangStructLiteral(new ArrayList<>(), var.type);
                break;
            case TypeTags.INT:
            case TypeTags.FLOAT:
            case TypeTags.STRING:
            case TypeTags.BOOLEAN:
            case TypeTags.BLOB:
            case TypeTags.XML:
                return;
            case TypeTags.TABLE:
            case TypeTags.STREAM:
                // TODO: add this once the able initializing is supported.
                return;
            default:
                return;
        }
        initExpr.pos = var.pos;

        BLangAssignment assignStmt = (BLangAssignment) TreeBuilder.createAssignmentNode();
        assignStmt.pos = var.pos;
        assignStmt.addVariable(varRef);
        assignStmt.expr = initExpr;

        transformerNode.body.stmts.add(0, assignStmt);
    }

    /**
     * Reorder the invocation arguments to match the original function signature.
     *
     * @param iExpr Function invocation expressions to reorder the arguments
     */
    private void reorderArguments(BLangInvocation iExpr) {
        BSymbol symbol = iExpr.symbol;

        if (symbol == null || symbol.type.tag != TypeTags.INVOKABLE) {
            return;
        }

        BInvokableSymbol invocableSymbol = (BInvokableSymbol) symbol;
        if (invocableSymbol.defaultableParams != null && !invocableSymbol.defaultableParams.isEmpty()) {
            // Re-order the named args
            reorderNamedArgs(iExpr, invocableSymbol);
        }

        if (invocableSymbol.restParam == null) {
            return;
        }

        // Create an array out of all the rest arguments, and pass it as a single argument.
        // If there is only one optional argument and its type is restArg (i.e: ...x), then
        // leave it as is.
        if (iExpr.restArgs.size() == 1 && iExpr.restArgs.get(0).getKind() == NodeKind.REST_ARGS_EXPR) {
            return;
        }
        BLangArrayLiteral arrayLiteral = (BLangArrayLiteral) TreeBuilder.createArrayLiteralNode();
        arrayLiteral.exprs = iExpr.restArgs;
        arrayLiteral.type = invocableSymbol.restParam.type;
        iExpr.restArgs = new ArrayList<>();
        iExpr.restArgs.add(arrayLiteral);
    }

    private void reorderNamedArgs(BLangInvocation iExpr, BInvokableSymbol invocableSymbol) {
        Map<String, BLangExpression> namedArgs = new HashMap<>();
        iExpr.namedArgs.forEach(expr -> namedArgs.put(((NamedArgNode) expr).getName().value, expr));

        // Re-order the named arguments
        List<BLangExpression> args = new ArrayList<>();
        for (BVarSymbol param : invocableSymbol.defaultableParams) {
            args.add(namedArgs.get(param.name.value));
        }
        iExpr.namedArgs = args;
    }

    private BLangMatchStmtPatternClause getSafeAssignErrorPattern(DiagnosticPos pos, BSymbol invokableSymbol) {
        // From here onwards we assume that this function has only one return type
        // Owner of the variable symbol must be an invokable symbol
        boolean noRetParams = ((BInvokableType) invokableSymbol.type).retTypes.isEmpty();
        boolean returnErrorType = false;
        if (!noRetParams) {
            BType retType = ((BInvokableType) invokableSymbol.type).retTypes.get(0);
            Set<BType> returnTypeSet = retType.tag == TypeTags.UNION ?
                    ((BUnionType) retType).memberTypes :
                    new HashSet<BType>() {{
                        add(retType);
                    }};
            returnErrorType = returnTypeSet
                    .stream()
                    .anyMatch(type -> types.isAssignable(type, symTable.errStructType));
        }

        // Create the pattern to match the error type
        //      1) Create the pattern variable
        String patternFailureCaseVarName = GEN_VAR_PREFIX.value + "t_failure";
        BLangVariable patternFailureCaseVar = ASTBuilderUtil.createVariable(pos,
                patternFailureCaseVarName, symTable.errStructType, null, new BVarSymbol(0,
                        names.fromString(patternFailureCaseVarName),
                        this.env.scope.owner.pkgID, symTable.errStructType, this.env.scope.owner));

        //      2) Create the pattern block
        BLangVariableReference patternFailureCaseVarRef = ASTBuilderUtil.createVariableRef(pos,
                patternFailureCaseVar.symbol);

        BLangBlockStmt patternBlockFailureCase = (BLangBlockStmt) TreeBuilder.createBlockNode();
        patternBlockFailureCase.pos = pos;
        if (noRetParams || !returnErrorType) {
            // throw e
            BLangThrow throwStmt = (BLangThrow) TreeBuilder.createThrowNode();
            throwStmt.pos = pos;
            throwStmt.expr = patternFailureCaseVarRef;
            patternBlockFailureCase.stmts.add(throwStmt);
        } else {
            //return e;
            BLangReturn returnStmt = (BLangReturn) TreeBuilder.createReturnNode();
            returnStmt.pos = pos;
            returnStmt.exprs = new ArrayList<BLangExpression>() {{
                add(patternFailureCaseVarRef);
            }};
            patternBlockFailureCase.stmts.add(returnStmt);
        }

        return ASTBuilderUtil.createMatchStatementPattern(pos, patternFailureCaseVar, patternBlockFailureCase);
    }

    private BLangMatchStmtPatternClause getSafeAssignSuccessPattern(DiagnosticPos pos,
                                                                    BType lhsType,
                                                                    boolean isVarDef,
                                                                    BVarSymbol varSymbol,
                                                                    BLangExpression lhsExpr) {
        //  File _$_f1 => f = _$_f1;
        // 1) Create the pattern variable
        String patternSuccessCaseVarName = GEN_VAR_PREFIX.value + "t_match";
        BLangVariable patternSuccessCaseVar = ASTBuilderUtil.createVariable(pos,
                patternSuccessCaseVarName, lhsType, null, new BVarSymbol(0,
                        names.fromString(patternSuccessCaseVarName),
                        this.env.scope.owner.pkgID, lhsType, this.env.scope.owner));

        //2) Create the pattern body
        BLangExpression varRefExpr;
        if (isVarDef) {
            varRefExpr = ASTBuilderUtil.createVariableRef(pos, varSymbol);
        } else {
            varRefExpr = lhsExpr;
        }

        BLangVariableReference patternSuccessCaseVarRef = ASTBuilderUtil.createVariableRef(pos,
                patternSuccessCaseVar.symbol);
        BLangAssignment assignmentStmtSuccessCase = ASTBuilderUtil.createAssignmentStmt(pos,
                new ArrayList<BLangExpression>() {{
                    add(varRefExpr);
                }}, patternSuccessCaseVarRef, false);

        BLangBlockStmt patternBlockSuccessCase = ASTBuilderUtil.createBlockStmt(pos,
                new ArrayList<BLangStatement>() {{
                    add(assignmentStmtSuccessCase);
                }});
        return ASTBuilderUtil.createMatchStatementPattern(pos,
                patternSuccessCaseVar, patternBlockSuccessCase);
    }

    private BLangStatement generateIfElseStmt(BLangMatch matchStmt, BLangVariable matchExprVar) {
        List<BLangMatchStmtPatternClause> patterns = matchStmt.patternClauses;
        if (patterns.size() == 1) {
            return getMatchPatternBody(patterns.get(0), matchExprVar);
        }

        BLangIf parentIfNode = generateIfElseStmt(patterns.get(0), matchExprVar);
        BLangIf currentIfNode = parentIfNode;
        for (int i = 1; i < patterns.size(); i++) {
            if (i == patterns.size() - 1) {
                // This is the last pattern
                currentIfNode.elseStmt = getMatchPatternBody(patterns.get(i), matchExprVar);
            } else {
                currentIfNode.elseStmt = generateIfElseStmt(patterns.get(i), matchExprVar);
                currentIfNode = (BLangIf) currentIfNode.elseStmt;
            }
        }

        // TODO handle json and any
        // only one pattern no if just a block
        // last one just a else block..
        // json handle it specially
        //
        return parentIfNode;
    }


    /**
     * Generate an if-else statement from the given match statement.
     *
     * @param patternClause match pattern statement node
     * @param matchExprVar  variable node of the match expression
     * @return if else statement node
     */
    private BLangIf generateIfElseStmt(BLangMatchStmtPatternClause patternClause, BLangVariable matchExprVar) {
        BLangExpression patternIfCondition = createPatternIfCondition(patternClause, matchExprVar.symbol);
        BLangBlockStmt patternBody = getMatchPatternBody(patternClause, matchExprVar);
        return ASTBuilderUtil.createIfElseStmt(patternClause.pos,
                patternIfCondition, patternBody, null);
    }

    private BLangBlockStmt getMatchPatternBody(BLangMatchStmtPatternClause patternClause, BLangVariable matchExprVar) {
        // Add the variable definition to the body of the pattern clause
        if (patternClause.variable.name.value.equals(Names.IGNORE.value)) {
            return patternClause.body;
        }

        // create TypeName i = <TypeName> _$$_
        // Create a variable reference for _$$_
        BLangSimpleVarRef matchExprVarRef = ASTBuilderUtil.createVariableRef(patternClause.pos,
                matchExprVar.symbol);
        BLangExpression patternVarExpr = addConversionExprIfRequired(matchExprVarRef, patternClause);

        // Add the variable def statement
        BLangVariable patternVar = ASTBuilderUtil.createVariable(patternClause.pos, "",
                patternClause.variable.type, patternVarExpr, patternClause.variable.symbol);
        BLangVariableDef patternVarDef = ASTBuilderUtil.createVariableDef(patternVar.pos, patternVar);
        patternClause.body.stmts.add(0, patternVarDef);
        return patternClause.body;
    }

    private BLangExpression addConversionExprIfRequired(BLangSimpleVarRef matchExprVarRef,
                                                        BLangMatchStmtPatternClause patternClause) {
        BType lhsType = patternClause.variable.type;
        BType rhsType = matchExprVarRef.type;
        if (types.isSameType(rhsType, lhsType)) {
            return matchExprVarRef;
        }

        types.setImplicitCastExpr(matchExprVarRef, rhsType, lhsType);
        if (matchExprVarRef.impConversionExpr != null) {
            return matchExprVarRef;
        }

        BConversionOperatorSymbol conversionSymbol;
        if (types.isValueType(lhsType) && !(rhsType.tag == TypeTags.JSON)) {
            conversionSymbol = Symbols.createUnboxValueTypeOpSymbol(symTable.anyType, lhsType);
        } else if (lhsType.tag == TypeTags.UNION || rhsType.tag == TypeTags.UNION) {
            conversionSymbol = Symbols.createConversionOperatorSymbol(rhsType, lhsType, symTable.errStructType,
                    false, true, InstructionCodes.NOP, null, null);
        } else {
            conversionSymbol = (BConversionOperatorSymbol) symResolver.resolveConversionOperator(rhsType, lhsType);
        }

        // Create a type cast expression
        BLangTypeConversionExpr conversionExpr = (BLangTypeConversionExpr)
                TreeBuilder.createTypeConversionNode();
        conversionExpr.expr = matchExprVarRef;
        conversionExpr.targetType = lhsType;
        conversionExpr.conversionSymbol = conversionSymbol;
        conversionExpr.type = lhsType;
        return conversionExpr;
    }

    private BLangExpression createPatternIfCondition(BLangMatchStmtPatternClause patternClause,
                                                     BVarSymbol varSymbol) {
        BLangExpression binaryExpr;
        BType patternType = patternClause.variable.type;
        BType[] memberTypes;
        if (patternType.tag == TypeTags.UNION) {
            BUnionType unionType = (BUnionType) patternType;
            memberTypes = unionType.memberTypes.toArray(new BType[0]);
        } else {
            memberTypes = new BType[1];
            memberTypes[0] = patternType;
        }

        if (memberTypes.length == 1) {
            binaryExpr = createPatternMatchBinaryExpr(patternClause.pos, varSymbol, memberTypes[0]);
        } else {
            BLangExpression lhsExpr = createPatternMatchBinaryExpr(patternClause.pos, varSymbol, memberTypes[0]);
            BLangExpression rhsExpr = createPatternMatchBinaryExpr(patternClause.pos, varSymbol, memberTypes[1]);
            binaryExpr = ASTBuilderUtil.createBinaryExpr(patternClause.pos, lhsExpr, rhsExpr,
                    symTable.booleanType, OperatorKind.OR,
                    (BOperatorSymbol) symResolver.resolveBinaryOperator(OperatorKind.OR,
                            lhsExpr.type, rhsExpr.type));
            for (int i = 2; i < memberTypes.length; i++) {
                lhsExpr = createPatternMatchBinaryExpr(patternClause.pos, varSymbol, memberTypes[i]);
                rhsExpr = binaryExpr;
                binaryExpr = ASTBuilderUtil.createBinaryExpr(patternClause.pos, lhsExpr, rhsExpr,
                        symTable.booleanType, OperatorKind.OR,
                        (BOperatorSymbol) symResolver.resolveBinaryOperator(OperatorKind.OR,
                                lhsExpr.type, rhsExpr.type));
            }
        }

        return binaryExpr;
    }

    private BLangExpression createPatternMatchBinaryExpr(DiagnosticPos pos, BVarSymbol varSymbol, BType patternType) {
        if (patternType == symTable.nullType) {
            BLangSimpleVarRef varRef = ASTBuilderUtil.createVariableRef(pos, varSymbol);
            BLangLiteral bLangLiteral = ASTBuilderUtil.createLiteral(pos, symTable.nullType, null);
            return ASTBuilderUtil.createBinaryExpr(pos, varRef, bLangLiteral, symTable.booleanType,
                    OperatorKind.EQUAL, (BOperatorSymbol) symResolver.resolveBinaryOperator(OperatorKind.EQUAL,
                            symTable.anyType, symTable.nullType));
        } else {
            return createIsAssignableExpression(pos, varSymbol, patternType);
        }
    }

    private BLangIsAssignableExpr createIsAssignableExpression(DiagnosticPos pos,
                                                               BVarSymbol varSymbol,
                                                               BType patternType) {
        //  _$$_ isassignable patternType
        // Create a variable reference for _$$_
        BLangSimpleVarRef varRef = ASTBuilderUtil.createVariableRef(pos, varSymbol);

        // Binary operator for equality
        return ASTBuilderUtil.createIsAssignableExpr(pos, varRef, patternType, symTable.booleanType, names);
    }

    private BLangExpression getInitExpr(BType type) {
        // Don't need to create an empty init expressions if the type allows null.
        if (type.isNullable()) {
            return null;
        }

        switch (type.tag) {
            case TypeTags.INT:
            case TypeTags.FLOAT:
            case TypeTags.BOOLEAN:
            case TypeTags.STRING:
            case TypeTags.BLOB:
                // Int, float, boolean, string, blob types will get default values from VM side.
                break;
            case TypeTags.JSON:
                return new BLangJSONLiteral(new ArrayList<>(), type);
            case TypeTags.XML:
                return new BLangXMLSequenceLiteral(type);
            case TypeTags.TABLE:
                return new BLangTableLiteral(type);
            case TypeTags.STREAM:
                return new BLangStreamLiteral(type, null);
            case TypeTags.MAP:
                return new BLangMapLiteral(new ArrayList<>(), type);
            case TypeTags.STRUCT:
                if (((BStructSymbol) type.tsymbol).isObject) {
                    return new BLangTypeInit();
                }
                return new BLangStructLiteral(new ArrayList<>(), type);
            case TypeTags.ARRAY:
                BLangArrayLiteral array = new BLangArrayLiteral();
                array.exprs = new ArrayList<>();
                array.type = type;
                return rewriteExpr(array);
            default:
                break;
        }
        return null;
    }

    // TODO: Same function is used in symbol enter. Refactor this to reuse the same function.
    private StatementNode createAssignmentStmt(BLangVariable variable) {
        BLangSimpleVarRef varRef = (BLangSimpleVarRef) TreeBuilder
                .createSimpleVariableReferenceNode();
        varRef.pos = variable.pos;
        varRef.variableName = variable.name;
        varRef.symbol = variable.symbol;
        varRef.type = variable.type;
        varRef.pkgAlias = (BLangIdentifier) TreeBuilder.createIdentifierNode();

        BLangAssignment assignmentStmt = (BLangAssignment) TreeBuilder.createAssignmentNode();
        assignmentStmt.expr = variable.expr;
        assignmentStmt.pos = variable.pos;
        assignmentStmt.addVariable(varRef);
        return assignmentStmt;
    }

}
