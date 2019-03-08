/*******************************************************************************
 * Copyright (c) 2018 Eclipse RDF4J contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/

package org.eclipse.rdf4j.sail.shacl.AST;

import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.sail.NotifyingSailConnection;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.shacl.RdfsSubClassOfReasoner;
import org.eclipse.rdf4j.sail.shacl.ShaclSailConnection;
import org.eclipse.rdf4j.sail.shacl.planNodes.ExternalTypeFilterNode;
import org.eclipse.rdf4j.sail.shacl.planNodes.LoggingNode;
import org.eclipse.rdf4j.sail.shacl.planNodes.PlanNode;
import org.eclipse.rdf4j.sail.shacl.planNodes.Select;
import org.eclipse.rdf4j.sail.shacl.planNodes.TrimTuple;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * sh:targetClass
 *
 * @author Heshan Jayasinghe
 */
public class TargetClass extends NodeShape {

	private final Set<Resource> targetClass;

	TargetClass(Resource id, SailRepositoryConnection connection, boolean deactivated, Set<Resource> targetClass) {
		super(id, connection, deactivated);
		this.targetClass = targetClass;
		assert !this.targetClass.isEmpty();

	}

	@Override
	public PlanNode getPlan(ShaclSailConnection shaclSailConnection, NodeShape nodeShape, boolean printPlans, PlanNode overrideTargetNode) {
		PlanNode parent = shaclSailConnection.getCachedNodeFor(new Select(shaclSailConnection, getQuery("?a", "?c", shaclSailConnection.getRdfsSubClassOfReasoner()), "*"));
		return new TrimTuple(new LoggingNode(parent, ""), 0, 1);
	}

	@Override
	public PlanNode getPlanAddedStatements(ShaclSailConnection shaclSailConnection, NodeShape nodeShape) {
		PlanNode cachedNodeFor = shaclSailConnection.getCachedNodeFor(new Select(shaclSailConnection.getAddedStatements(), getQuery("?a", "?c", null), "*"));
		return new TrimTuple(new LoggingNode(cachedNodeFor, ""), 0, 1);

	}

	@Override
	public PlanNode getPlanRemovedStatements(ShaclSailConnection shaclSailConnection, NodeShape nodeShape) {
		PlanNode parent = shaclSailConnection.getCachedNodeFor(new Select(shaclSailConnection.getRemovedStatements(), getQuery("?a", "?c", null), "*"));
		return new TrimTuple(parent, 0, 1);
	}

	@Override
	public boolean requiresEvaluation(SailConnection addedStatements, SailConnection removedStatements) {
		return targetClass
			.stream()
			.map(target -> addedStatements.hasStatement(null, RDF.TYPE, target, false))
			.reduce((a, b) -> a || b)
			.orElseThrow(IllegalStateException::new);

	}

	@Override
	public String getQuery(String subjectVariable, String objectVariable, RdfsSubClassOfReasoner rdfsSubClassOfReasoner) {
		Set<Resource> targets = targetClass;

		if (rdfsSubClassOfReasoner != null) {
			targets = new HashSet<>(targets);

			targets = targets
				.stream()
				.flatMap(target -> rdfsSubClassOfReasoner.backwardsChain(target).stream())
				.collect(Collectors.toSet());
		}

		if (targets.size() > 1) {
			return targets
				.stream()
				.map(r -> "{ BIND(rdf:type as ?b1) \n BIND(<" + r + "> as " + objectVariable + ") \n " + subjectVariable + " ?b1 " + objectVariable + ". } \n")
				.reduce((l, r) -> l + " UNION " + r)
				.get();
		}

		if(targets.size() == 0){
			System.out.println();
		}
		assert targets.size() == 1;

		return "BIND(rdf:type as ?b1) \n BIND(<" + targets.stream().findAny().get() + "> as " + objectVariable + ") \n " + subjectVariable + " ?b1 " + objectVariable + ". \n";
	}

	@Override
	public PlanNode getTargetFilter(NotifyingSailConnection shaclSailConnection, PlanNode parent) {
		return new ExternalTypeFilterNode(shaclSailConnection, targetClass, parent, 0, true);
	}

}
