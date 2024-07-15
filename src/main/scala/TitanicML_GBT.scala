import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.ml.feature.{StringIndexer, VectorAssembler}
import org.apache.spark.ml.classification.GBTClassifier
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator

object TitanicML_GBT {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder
      .appName("Titanic GBT Classifier")
      .master("local[*]")
      .getOrCreate()

    // Leer el archivo CSV
    val df = spark.read.option("header", "true").csv("data/Titanic-Dataset.csv")

    // Preprocesamiento de datos
    val dfClean = df.withColumn("Age", col("Age").cast("double"))
      .withColumn("Survived", col("Survived").cast("int"))
      .withColumn("Pclass", col("Pclass").cast("int"))
      .withColumn("SibSp", col("SibSp").cast("int"))
      .withColumn("Parch", col("Parch").cast("int"))
      .withColumn("Fare", col("Fare").cast("double"))

    val dfNoNulls = dfClean.na.fill(Map(
      "Age" -> dfClean.agg(avg("Age")).first().getDouble(0),
      "Embarked" -> "S"
    ))

    // Convertir columnas categóricas en índices
    val indexerSex = new StringIndexer()
      .setInputCol("Sex")
      .setOutputCol("SexIndex")

    val indexerEmbarked = new StringIndexer()
      .setInputCol("Embarked")
      .setOutputCol("EmbarkedIndex")

    val dfIndexed = indexerSex.fit(dfNoNulls).transform(dfNoNulls)
    val dfFinal = indexerEmbarked.fit(dfIndexed).transform(dfIndexed)

    // Ensamblar características para el modelo
    val assembler = new VectorAssembler()
      .setInputCols(Array("Pclass", "SexIndex", "Age", "SibSp", "Parch", "Fare", "EmbarkedIndex"))
      .setOutputCol("features")

    val dfFeatures = assembler.transform(dfFinal)

    // Dividir dataset en entrenamiento y prueba
    val Array(trainingData, testData) = dfFeatures.randomSplit(Array(0.7, 0.3))

    // Crear modelo GBT
    val gbt = new GBTClassifier()
      .setLabelCol("Survived")
      .setFeaturesCol("features")
      .setMaxIter(100)

    // Entrenar modelo
    val gbtModel = gbt.fit(trainingData)

    // Hacer predicciones
    val gbtPredictions = gbtModel.transform(testData)

    // Evaluar modelo
    val evaluator = new BinaryClassificationEvaluator()
      .setLabelCol("Survived")
      .setMetricName("areaUnderROC")

    val gbtAccuracy = evaluator.evaluate(gbtPredictions)
    println(s"Área bajo la curva ROC para Gradient-Boosted Trees: $gbtAccuracy")

    // Mostrar algunas predicciones
    gbtPredictions.select("Survived", "prediction", "probability").show(10)

    // Detener la sesión de Spark
    spark.stop()
  }
}
