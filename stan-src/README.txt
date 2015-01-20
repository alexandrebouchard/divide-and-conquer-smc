Notes for stan cf:

1) downloaded cmd stan in:

/Users/bouchard/Documents/workspace-cpp/cmdstan

2) referred to doc/cmdstan..pdf in the same dir

3) built stan with:

make bin/libstan.a

4) generated and built stan model using

multilevel.mcmc

nb: it takes half a day or more; result symlinked in 

stan-src/final

5) ran stan using

./model sample num_samples=200 num_warmup=200 data file=data.R

6) to get ESS:

/Users/bouchard/Documents/workspace-cpp/cmdstan/bin/print

ALSO: see ReadStanOutput