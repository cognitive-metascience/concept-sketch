$udpExe = 'D:/git/word-sketch-lucene/udpipe_bin/udpipe.exe'
$udpModel = 'D:/git/word-sketch-lucene/udpipe_bin/english-ewt-ud-2.5-191206.udpipe'

# Run UDPipe on remaining jobs 0 and 3
& $udpExe --tokenize --tag --output=conllu --outfile='D:/corpus_74m/temp/udpipe_0.conllu' $udpModel 'D:/corpus_74m/temp/udpipe_0.txt' 2>$null
& $udpExe --tokenize --tag --output=conllu --outfile='D:/corpus_74m/temp/udpipe_3.conllu' $udpModel 'D:/corpus_74m/temp/udpipe_3.txt' 2>$null

Write-Host "Done! Check output sizes:"
(Get-ChildItem 'D:/corpus_74m/temp/udpipe_*.conllu').Length/1MB
