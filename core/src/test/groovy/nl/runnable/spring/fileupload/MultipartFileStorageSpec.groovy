package nl.runnable.spring.fileupload

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

/**
 * @author Laurens Fridael
 */
@ContextConfiguration(classes = [TestConfig])
@Transactional
class MultipartFileStorageSpec extends Specification {

  @Autowired
  MultipartFileStorage storage

  String fileId

  String context = 'test-context'

  String otherContext = 'another-context'

  String metadata = 'some-metadata'

  def setup() {
    def file = new MockMultipartFile(
        'file',
        'test.pdf',
        'application/pdf',
        [1, 2, 3, 4] as byte[]
    )
    fileId = storage.save(file, MultipartFileStorage.TTL_30_MINUTES, context, metadata)
    storage.save(file, MultipartFileStorage.TTL_30_MINUTES, context, metadata)
    storage.save(file, MultipartFileStorage.TTL_30_MINUTES, "another-context", metadata)
  }

  def 'Retrieving a file yields data that is equivalent to that of the input file'() {
    when:
    def file = storage.find(fileId)
    def tempFile = File.createTempFile('test', 'file')
    file.transferTo(tempFile)
    then:
    file.id == fileId
    file.name == 'file'
    file.originalFilename == 'test.pdf'
    file.contentType == 'application/pdf'
    file.size == 4
    !file.empty
    file.bytes == [1, 2, 3, 4] as byte[]
    file.id == fileId
    file.context == context
    file.metadata == metadata
    file.createdAt
    file.expiresAt.time == file.createdAt.time + (MultipartFileStorage.TTL_30_MINUTES * 1000)
  }

  def 'Filtering files by context yields the matching files'() {
    expect:
    storage.findByContext(context).size == 2
  }

  def "Transferring a multipart file's contents to a system file yields identical content"() {
    setup:
    def file = storage.find(fileId)
    def tempFile = File.createTempFile(file.originalFilename, '.tmp')
    tempFile.deleteOnExit()

    when:
    file.transferTo(tempFile)
    then:
    tempFile.bytes == [1, 2, 3, 4] as byte[]

    cleanup:
    tempFile.delete()
  }

  def "Setting a file's time-to-live changes its expiration"() {
    when:
    def expiresAt = storage.setTimeToLive(fileId, 1000)
    def file = storage.find(fileId)
    then:
    expiresAt.time == file.expiresAt.time
  }

  def "Setting a file's metadata makes the new metadata retrievable in subsequent find() invocations."() {
    when:
    storage.setMetadata(fileId, 'new-metadata')
    def file = storage.find(fileId)
    then:
    file.metadata == 'new-metadata'
  }

  def 'Deleting a file removes it from storage'() {
    expect:
    storage.delete(fileId) == 1
    !storage.find(fileId)
    storage.delete(fileId) == 0
  }

  def 'Deleting expired files removes them from storage'() {
    expect:
    storage.deleteExpired() == 0

    when:
    storage.setTimeToLive(fileId, 0)
    then:
    storage.deleteExpired() == 1
    !storage.find(fileId)
  }

  def 'Deleting files by context removes them from storage'() {
    expect:
    storage.deleteByContext(context) == 2
    storage.deleteByContext(otherContext) == 1
    storage.count() == 0
  }

  def 'Deleting all files removes them from storage'() {
    expect:
    storage.deleteAll() == 3
    storage.count() == 0
  }

  def 'Can obtain the number of stored files'() {
    expect:
    storage.count() == 3
  }

  def 'Can save a file using a predefined ID'() {
    given:
    def file = new MockMultipartFile(
        'another-file',
        'another-file.pdf',
        'application/pdf',
        [5, 6, 7, 8] as byte[]
    )
    storage.save(file, 'predefined-id', MultipartFileStorage.TTL_30_MINUTES, null, null)
    expect:
    storage.find('predefined-id')
  }

  def 'Cannot save a file using a duplicate ID'() {
    given:
    def file = new MockMultipartFile(
        'another-file',
        'another-file.pdf',
        'application/pdf',
        [5, 6, 7, 8] as byte[]
    )
    when:
    storage.save(file, fileId, MultipartFileStorage.TTL_30_MINUTES, null, null)
    then:
    thrown(Exception)
  }

}
