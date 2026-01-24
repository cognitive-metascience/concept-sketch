$udpExe = 'D:/git/word-sketch-lucene/udpipe_bin/udpipe.exe'
$udpModel = 'D:/git/word-sketch-lucene/udpipe_bin/english-ewt-ud-2.5-191206.udpipe'
& $udpExe --tokenize --tag --output=conllu $udpModel 'D:/corpus_74m/temp/udpipe_1.txt' *>'D:/corpus_74m/temp/udpipe_1.conllu'
