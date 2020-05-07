package hu.bme.mit.inf.dslreasoner.viatrasolver.reasoner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.viatra.query.runtime.api.IPatternMatch;
import org.eclipse.viatra.query.runtime.api.ViatraQueryEngine;
import org.eclipse.viatra.query.runtime.api.ViatraQueryMatcher;
import org.eclipse.viatra.query.runtime.emf.EMFScope;
import org.eclipse.viatra.transformation.evm.api.RuleEngine;
import org.eclipse.viatra.transformation.runtime.emf.rules.batch.BatchTransformationRule;
import org.eclipse.xtend2.lib.StringConcatenation;

import com.google.common.base.Objects;

import hu.bme.mit.inf.dslreasoner.logic.model.builder.DocumentationLevel;
import hu.bme.mit.inf.dslreasoner.logic.model.builder.LogicModelInterpretation;
import hu.bme.mit.inf.dslreasoner.logic.model.builder.LogicReasoner;
import hu.bme.mit.inf.dslreasoner.logic.model.builder.LogicReasonerException;
import hu.bme.mit.inf.dslreasoner.logic.model.builder.LogicSolverConfiguration;
import hu.bme.mit.inf.dslreasoner.logic.model.logicproblem.LogicProblem;
import hu.bme.mit.inf.dslreasoner.logic.model.logicresult.LogicResult;
import hu.bme.mit.inf.dslreasoner.logic.model.logicresult.ModelResult;
import hu.bme.mit.inf.dslreasoner.viatrasolver.logic2viatra.ModelGenerationMethod;
import hu.bme.mit.inf.dslreasoner.viatrasolver.logic2viatra.ModelGenerationMethodProvider;
import hu.bme.mit.inf.dslreasoner.viatrasolver.logic2viatra.ScopePropagator;
import hu.bme.mit.inf.dslreasoner.viatrasolver.partialinterpretationlanguage.PartialInterpretationInitialiser;
import hu.bme.mit.inf.dslreasoner.viatrasolver.partialinterpretationlanguage.partialinterpretation.PartialInterpretation;
import hu.bme.mit.inf.dslreasoner.viatrasolver.reasoner.dse.PartialModelAsLogicInterpretation;
import hu.bme.mit.inf.dslreasoner.workspace.ReasonerWorkspace;

public class StochasticSimulator extends LogicReasoner{
	
	private final PartialInterpretationInitialiser initialiser = new PartialInterpretationInitialiser();
	private final ModelGenerationMethodProvider modelGenerationMethodProvider = new ModelGenerationMethodProvider();

	@Override
	public LogicResult solve(LogicProblem problem, LogicSolverConfiguration configuration, ReasonerWorkspace workspace)
			throws LogicReasonerException {

		final ViatraReasonerConfiguration viatraConfig = this.asConfig(configuration);
		final PartialInterpretation emptySolution = this.initialiser.initialisePartialInterpretation(problem, viatraConfig.typeScopes).getOutput();

		emptySolution.setProblemConainer(problem);
		final ScopePropagator scopePropagator = new ScopePropagator(emptySolution);
		final ModelGenerationMethod method = this.modelGenerationMethodProvider.createModelGenerationMethod(problem, emptySolution, workspace, 
			      viatraConfig.nameNewElements, 
			      viatraConfig.typeInferenceMethod, scopePropagator, 
			      viatraConfig.documentationLevel);
		
		ViatraQueryEngine engine = ViatraQueryEngine.on(new EMFScope(emptySolution));
		
		ArrayList<ViatraQueryMatcher<?>> matcherList = new ArrayList<>();
		Map<ViatraQueryMatcher<?>,Consumer<?>> matcher2Action = new HashMap<>();
		for (BatchTransformationRule<?, ?> objectRefinementRule : method.getObjectRefinementRules()) {
			ViatraQueryMatcher<?> matcher = engine.getMatcher(objectRefinementRule.getPrecondition());
			Consumer<?> action = objectRefinementRule.getAction();
			matcher2Action.put(matcher,action);
			matcherList.add(matcher);
		}
		
		ArrayList<IPatternMatch> currentMatches = new ArrayList<>();
		Map<IPatternMatch,ViatraQueryMatcher<?>> match2Matcher = new HashMap<>();
		for(int i = 0; i < 100; ++i)
		{
			ArrayList<IPatternMatch> tempMatches = new ArrayList<>();
			for(int j = 0; j < matcherList.size(); ++j)
			{
				ViatraQueryMatcher<?> tempMatcher = matcherList.get(j);
				tempMatches.clear();
				tempMatches.addAll(tempMatcher.getAllMatches());
				currentMatches.addAll(tempMatches);
				for(int k = 0; k < tempMatches.size(); ++k)
				{
					match2Matcher.put(tempMatches.get(k), tempMatcher);
				}
			}
			Random rand = new Random();
			if(currentMatches.size() > 0)
			{
				int index = rand.nextInt(currentMatches.size());
				IPatternMatch selectedMatch = currentMatches.get(index);
				ViatraQueryMatcher<?> selectedMatcher = match2Matcher.get(selectedMatch);
				matcher2Action.get(selectedMatcher).accept(selectedMatch);
			}
		}
		return null;
	}
	
	private ViatraReasonerConfiguration asConfig(final LogicSolverConfiguration configuration) {
	    if ((configuration instanceof ViatraReasonerConfiguration)) {
	      return ((ViatraReasonerConfiguration)configuration);
	    } else {
	      StringConcatenation _builder = new StringConcatenation();
	      _builder.append("Wrong configuration. Expected: ");
	      String _name = ViatraReasonerConfiguration.class.getName();
	      _builder.append(_name);
	      _builder.append(", but got: ");
	      String _name_1 = configuration.getClass().getName();
	      _builder.append(_name_1);
	      _builder.append("\"");
	      throw new IllegalArgumentException(_builder.toString());
	    }
	  }

	@Override
	public List<? extends LogicModelInterpretation> getInterpretations(ModelResult modelResult) {
		List<PartialModelAsLogicInterpretation> result = new LinkedList<PartialModelAsLogicInterpretation>();
		List<Map<EObject, EObject>> traces =  (List<Map<EObject, EObject>>) modelResult.getTrace();
		for(int index=0; index<modelResult.getRepresentation().size();index++) {
			PartialModelAsLogicInterpretation model = new PartialModelAsLogicInterpretation((PartialInterpretation) modelResult.getRepresentation().get(index), traces.get(index));
			result.add(model);
		}
		return result;
	}

}
