NETS 1500 – HW5: The Reviewer Network
Julia Susser, Mudit Marwaha, Dhruv Bhargava

Project Description
-------------------
The Reviewer Network is a Java desktop application that analyzes the Amazon Fine Foods
dataset (Stanford SNAP, ~568,000 reviews). It models the review data as a bipartite
graph connecting users and products, weighted by star rating. The application provides
three main features: a TF-IDF search engine that ranks products by cosine similarity
to a free-text query, a Personalized PageRank recommender that suggests unseen products
based on a user's review history, and a network statistics panel displaying degree
distributions and top reviewers/products.

Dataset Setup
---------------------------

The application uses the Amazon Fine Foods dataset from the Stanford SNAP repository:

Source: https://snap.stanford.edu/data/web-FineFoods.html
Direct file: finefoods.txt
Setup Instructions
Download the dataset from the link above.

In the project root directory, manually create an input/ folder:

  mkdir input

Place the downloaded file inside:

  input/finefoods.txt

Compile (from project root)
---------------------------
javac -d out src/data/*.java src/graph/*.java src/analysis/*.java \
  src/recommendation/*.java src/app/*.java src/Main.java
 
Run
---
Default (looks for input/finefoods.txt):
  java -cp out Main
 
Custom data path:
  java -cp out Main --data input/finefoods.txt


Categories Used
---------------
- Graph and graph algorithms (bipartite user-product graph, Personalized PageRank)
- Document search and information retrieval (TF-IDF, cosine similarity)
- Social networks and recommender systems (random walk with restart)

Work Breakdown
--------------
Julia Susser: TF-IDF engine, document retrieval, cosine similarity, personalized PageRank recommender, documentation and user manual
Mudit Marwaha: Graph construction, bipartite graph model, network statistics module, user manual
Dhruv Bhargava: Swing GUI, data loading and parsing, user manual

AI Usage
--------
We used AI (Claude) to help with writing code syntax, debugging, and LaTeX formatting
for the writeup. All algorithms, design decisions, architecture, and function structure
were our own work. AI was most used when coding the java swing GraphViewer page.
