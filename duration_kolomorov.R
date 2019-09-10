require(ggplot2)

gatling <- read.csv(sep=";", file = '/Users/d072521/Library/Mobile Documents/com~apple~CloudDocs/SRH Master/Thesis/Latex/src/Performance-comparison/loadtest4jsimulation-1566503081945-lock2-60connpool/gatling-result.csv')
head(gatling)
str(gatling)
summary(gatling)


gatling1 <- read.csv(sep=";", file = '/Users/d072521/Library/Mobile Documents/com~apple~CloudDocs/SRH Master/Thesis/Latex/src/Performance-comparison/Vergleich/loadtest4jsimulation-1567881312535 - LockLT ohne autoexplain/simulation_converted_clean.csv')
head(gatling1)
str(gatling1)
summary(gatling1)


gatling2 <- read.csv(sep=";", file = '/Users/d072521/Library/Mobile Documents/com~apple~CloudDocs/SRH Master/Thesis/Latex/src/Performance-comparison/Vergleich/loadtest4jsimulation-1567882203539 - LockLT ohne loging at all/simulation_converted.csv')
head(gatling2)
str(gatling2)
summary(gatling2)


#install.packages("dgof")
#require(dgof)
ks.test(gatling$duration, gatling1$duration) 
ks.test(gatling$duration, gatling1$duration, alternative = "greater")
ks.test(gatling$duration, gatling1$duration, alternative = "less") 

ks.test(gatling$duration, gatling2$duration) 
ks.test(gatling$duration, gatling2$duration, alternative = "greater")
ks.test(gatling$duration, gatling2$duration, alternative = "less") 


#https://davetang.org/muse/2012/04/17/comparing-different-distributions/
# The null hypothesis is that both samples come from the same distribution and is rejected (p-value = 0.01166 (98% signifikanzniveau)).
# You reject the null hypothesis that the two samples were drawn from the same distribution if the p-value is less than your significance level.







df <- rbind(
  + data.frame(group='A', duration=gatling$duration),
  + data.frame(group='B', duration=gatling2$duration))
head(df)
summary(df) # fix me

# perform t-test for the difference of means
 t.test(outcome ~ duration, data=df)

# draw a histogram
ggplot(gatling2, aes(duration)) + geom_histogram(binwidth=100) 
ggplot(gatling, aes(duration)) + geom_histogram(binwidth=100) 

# bin/discretize (cut continuous variable into equally sized groups)
(q<-quantile(gatling$duration , seq(0, 1, .2)))
df$duration_bin <- cut(gatling$duration, breaks=q, include.lowest=T)
summary(df$duration_bin)
 
# tabulate the binned data
(tab<-with(df, table(duration_bin, group))) # counts group

m <- table(gatling$duration, gatling2$duration)
str(m)
summary(m)

(Xsq <- chisq.test(m))  # Prints test summary
Xsq$observed   # observed counts (same as M)
Xsq$expected   # expected counts under the null
Xsq$residuals  # Pearson residuals
Xsq$stdres     # standardized residuals
