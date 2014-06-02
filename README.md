# Multi-Level SMC

A Divide and Conquer SMC inference for a multi-level hierarchical model with a tree
structured normal prior and binomial observations at the leaves.

The paper explaining this method is under review. Please contact us if you would like to use this software.

Installing from source
----------------------

Requires: java, gradle, git

- Clone the repository
- Type ``gradle installApp`` from the root of the repository
- Optionally, put in your PATH variable the directory ``build/install/multilevelSMC/bin``

Running the software
--------------------

- Download and prepare the data by typing ``scripts/prepare-data.sh`` from the root of the repository (requires wget). This writes the preprocessed data in ``data/preprocessedNYSData.csv``
- Run the software using ``build/install/multilevelSMC/bin/multilevelSMC -inputData data/preprocessedNYSData.csv -nParticles 1000``. 
  - Note: for the plots to work, you need to have R in your PATH variable (more precisely, Rscript) 
  - Various output files are written in ``results/latest/``


