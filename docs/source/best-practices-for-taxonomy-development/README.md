---
title: Best practices for taxonomy development
description: Taxi should be used with reusability in mind
---

## Goals of effective taxonomies

* Enable teams and systems to share, discover and transform data freely, and automatically
* Allow teams and systems to evolve independently

## Antipatterns

### Consumers of a central taxonomy have to evolve together

Changes to your central taxonomy should not require systems to adapt their platform, or  require multiple consumers to update at the same time.

If this is the case, it may indicate your taxonomy is a series of models, rather than types

### Common Domain Model

A common domain model is where multiple systems across an organisation \(or across multiple organisations\) agree on a single shared definition of models.  

The rationale for this approach is typically to reduce integration costs, and lower the percieved inefficiencies of duplication.  In practice, there is seldom a single "best" or canonical way to represent a concept, meaning that the result often ends up serving no-one well.

The result of a Common Domain Model is that:

* Teams lose their ability to innovate and iterate at their own pace
* Evolution of models must go through a design comitee, and seek broad signoff, driving beuracracy, eliminating team autonomy.
* Concepts that apply to only a subset of the consumers either get rejected by the community, or result in diluted representations, as not all consumers can publish or consume the data
* Teams will typically treat the Common Domain Model as a boundary concern, mapping their own internal representation onto it.  

In effect, Common Domain Models become self-defeating, as the cost of integration has simply been moved one layer of abstraction, rather than eliminated.
