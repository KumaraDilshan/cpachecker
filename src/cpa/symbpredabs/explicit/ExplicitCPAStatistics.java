/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker. 
 *
 *  Copyright (C) 2007-2008  Dirk Beyer and Erkan Keremoglu.
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://www.cs.sfu.ca/~dbeyer/CPAchecker/
 */
package cpa.symbpredabs.explicit;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import symbpredabstraction.bdd.BDDPredicate;
import symbpredabstraction.interfaces.Predicate;
import symbpredabstraction.interfaces.PredicateMap;
import symbpredabstraction.mathsat.MathsatSymbolicFormula;
import cfa.objectmodel.CFAEdge;
import cfa.objectmodel.CFANode;
import cmdline.CPAMain;
import cmdline.CPAMain.Result;

import common.Pair;

import cpaplugin.CPAStatistics;


/**
 * Statistics relative to Explicit-state lazy abstraction.
 *
 * @author Alberto Griggio <alberto.griggio@disi.unitn.it>
 */
public class ExplicitCPAStatistics implements CPAStatistics {

    private ExplicitCPA cpa;

    public ExplicitCPAStatistics(ExplicitCPA cpa) {
        this.cpa = cpa;
    }

    @Override
    public String getName() {
        return "Explicit-State Predicate Abstraction";
    }

    @Override
    public void printStatistics(PrintWriter out, Result result) {
        ExplicitTransferRelation trans =
            (ExplicitTransferRelation)cpa.getTransferRelation();
        PredicateMap pmap = cpa.getPredicateMap();
        BDDMathsatExplicitAbstractManager amgr =
            (BDDMathsatExplicitAbstractManager)cpa.getAbstractFormulaManager();

        Set<Predicate> allPreds = new HashSet<Predicate>();
        Collection<CFANode> allLocs = null;
        Collection<String> allFuncs = null;
        int maxPreds = 0;
        int totPreds = 0;
        int avgPreds = 0;
        if (!CPAMain.cpaConfig.getBooleanValue(
                "cpas.symbpredabs.refinement.addPredicatesGlobally")) {
            allLocs = pmap.getKnownLocations();
            for (CFANode l : allLocs) {
                Collection<Predicate> p = pmap.getRelevantPredicates(l);
                maxPreds = Math.max(maxPreds, p.size());
                totPreds += p.size();
                allPreds.addAll(p);
            }
            avgPreds = allLocs.size() > 0 ? totPreds/allLocs.size() : 0;
        } else {
            allFuncs = pmap.getKnownFunctions();
            for (String s : allFuncs) {
                Collection<Predicate> p = pmap.getRelevantPredicates(s);
                maxPreds = Math.max(maxPreds, p.size());
                totPreds += p.size();
                allPreds.addAll(p);
            }
            avgPreds = allFuncs.size() > 0 ? totPreds/allFuncs.size() : 0;
        }

        // check if/where to dump the predicate map
        if (result == Result.SAFE) {
          String outfilePath = CPAMain.cpaConfig.getProperty("output.path");
          String outfileName = CPAMain.cpaConfig.getProperty(
              "cpas.symbpredabs.refinement.finalPredMapFile", "");
          if (outfileName == null) {
            outfileName = "predmap.txt";
          }
            if (!outfileName.equals("")) {
                File f = new File(outfilePath + outfileName);
                try {
                    PrintWriter pw = new PrintWriter(f);
                    pw.println("ALL PREDICATES:");
                    for (Predicate p : allPreds) {
                        Pair<MathsatSymbolicFormula, MathsatSymbolicFormula> d =
                            amgr.getPredicateNameAndDef((BDDPredicate)p);
                        pw.format("%s ==> %s <-> %s\n", p, d.getFirst(),
                                d.getSecond());
                    }
                    if (!CPAMain.cpaConfig.getBooleanValue(
                        "cpas.symbpredabs.refinement.addPredicatesGlobally")) {
                        pw.println("\nFOR EACH LOCATION:");
                        for (CFANode l : allLocs) {
                            Collection<Predicate> c =
                                pmap.getRelevantPredicates(l);
                            pw.println("LOCATION: " + l);
                            for (Predicate p : c) {
                                pw.println(p);
                            }
                            pw.println("");
                        }
                    }
                    pw.close();
                } catch (FileNotFoundException e) {
                    // just issue a warning to the user
                    out.println("WARNING: impossible to dump predicate map on `"
                                + outfilePath + outfileName + "'");
                }
            }
        }

        BDDMathsatExplicitAbstractManager.Stats bs = amgr.getStats();

        out.println("Number of abstract states visited: " +
                trans.getNumAbstractStates());
        out.println("Number of abstraction steps: " + bs.numCallsAbstraction);
        if (!bs.edgeAbstCountMap.isEmpty()) {
            out.println("Number of abstraction steps per each edge:");
            Vector<Pair<Integer, CFAEdge>> v =
                new Vector<Pair<Integer, CFAEdge>>();
            for (CFAEdge e : bs.edgeAbstCountMap.keySet()) {
                v.add(new Pair<Integer, CFAEdge>(
                        bs.edgeAbstCountMap.get(e), e));
            }
            Collections.sort(v, new Comparator<Pair<Integer, CFAEdge>>() {
                public int compare(Pair<Integer, CFAEdge> o1,
                        Pair<Integer, CFAEdge> o2) {
                    int r = (o2.getFirst() - o1.getFirst());
                    if (r == 0) {
                        return o1.getSecond().getPredecessor().getNodeNumber() -
                               o2.getSecond().getPredecessor().getNodeNumber();
                    } else {
                        return r;
                    }
                }
            });
            for (Pair<Integer, CFAEdge> p : v) {
                out.println("  " + p.getSecond() + " : " + p.getFirst());
            }
        }
        out.println("Number of SMT queries in abstraction: " +
                (bs.abstractionNumMathsatQueries +
                 bs.abstractionNumCachedQueries) + " total, " +
                 bs.abstractionNumCachedQueries + " cached");
        out.println("Number of refinement steps: " + bs.numCallsCexAnalysis);
        out.println("");
        out.println("Total number of predicates discovered: " +
                allPreds.size());
        out.println("Average number of predicates per location: " + avgPreds);
        out.println("Max number of predicates per location: " + maxPreds);
        out.println("");
        out.println("Total time for abstraction computation: " +
                toTime(bs.abstractionMathsatTime + bs.abstractionBddTime));
        out.println("  Time for All-SMT: ");
        out.println("    Total:             " +
                toTime(bs.abstractionMathsatTime));
        out.println("    Max:               " +
                toTime(bs.abstractionMaxMathsatTime));
        out.println("    Solving time only: " +
                toTime(bs.abstractionMathsatSolveTime));
        out.println("  Time for BDD construction: ");
        out.println("    Total:             " + toTime(bs.abstractionBddTime));
        out.println("    Max:               " +
                toTime(bs.abstractionMaxBddTime));
        out.println(
                "Time for counterexample analysis/abstraction refinement: ");
        out.println("  Total:               " + toTime(bs.cexAnalysisTime));
        out.println("  Max:                 " + toTime(bs.cexAnalysisMaxTime));
        out.println("  Solving time only:   " +
                toTime(bs.cexAnalysisMathsatTime));
        if (CPAMain.cpaConfig.getBooleanValue(
                "cpas.symbpredabs.explicit.getUsefulBlocks")) {
            out.println("  Cex.focusing total:  " +
                    toTime(bs.cexAnalysisGetUsefulBlocksTime));
            out.println("  Cex.focusing max:    " +
                toTime(bs.cexAnalysisGetUsefulBlocksMaxTime));
        }
        if (CPAMain.cpaConfig.getBooleanValue(
                "cpas.symbpredabs.explicit.extendedStats")) {
            out.println("Extended statistics:");
            out.println("  Cache lookup time:         " +
                    toTime(bs.cacheLookupTime));
            out.println("  Term build time:           " +
                    toTime(bs.termBuildTime));
            out.println("  Msat term copy time:       " +
                    toTime(bs.msatTermCopyTime));
            out.println("  Predicate extraction time: " +
                    toTime(bs.predicateExtractionTime));
            out.println("  Extra time:                " + toTime(bs.extraTime));
            out.println("    Sub 1:                   " +
                    toTime(bs.extraTimeSub1));
            out.println("  Calls to makeFormula: " + bs.makeFormulaCalls);
            out.println("  Cache hits in makeFormula: " +
                    bs.makeFormulaCacheHits);
        }
        if (trans.notEnoughPredicates()) {
            out.println("The analysis is not precise enough for this example!");
        }
    }

    private String toTime(long timeMillis) {
//        return String.format("%02dh:%02dm:%02d.%03ds",
//                timeMillis / (1000 * 60 * 60),
//                timeMillis / (1000 * 60),
//                timeMillis / 1000,
//                timeMillis % 1000);
        return String.format("% 5d.%03ds", timeMillis/1000, timeMillis%1000);
    }

}
